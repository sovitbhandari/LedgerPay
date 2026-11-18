-- LedgerPay schema.
-- Money is stored only as BIGINT minor units. Currency scale is an application rule
-- (see com.ledgerpay.money.LedgerCurrency), not a floating-point column.
--
-- Transaction boundary for a successful posting (see LedgerPostingService):
--   1. Insert the idempotency claim. The unique key blocks a concurrent duplicate.
--   2. Lock both account rows in ascending id order (SELECT ... FOR UPDATE).
--   3. Insert the transfer and exactly two ledger lines, then update both balances.
--   4. Attach the transfer id to the idempotency claim.
--   5. Commit. Deferred constraints reject an unbalanced transfer or a claim with no transfer.
-- Any failure before commit rolls the claim, the transfer, the lines, and the balances back together.

CREATE TABLE app_users (
    id UUID PRIMARY KEY,
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    system_account BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT app_users_email_lowercase CHECK (email = lower(email)),
    CONSTRAINT uq_app_users_email UNIQUE (email)
);

CREATE TABLE accounts (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES app_users (id),
    name VARCHAR(80) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(16) NOT NULL,
    kind VARCHAR(32) NOT NULL,
    balance_minor BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT accounts_currency_iso CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT accounts_status_chk CHECK (status IN ('ACTIVE', 'FROZEN', 'CLOSED')),
    CONSTRAINT accounts_kind_chk CHECK (kind IN ('CUSTOMER', 'SYSTEM_FUNDING')),
    CONSTRAINT accounts_customer_balance_non_negative CHECK (
        kind = 'SYSTEM_FUNDING' OR balance_minor >= 0
    ),
    CONSTRAINT accounts_system_funding_stays_active CHECK (
        kind <> 'SYSTEM_FUNDING' OR status = 'ACTIVE'
    )
);

COMMENT ON COLUMN accounts.balance_minor IS
    'Projection of ledger entries, updated in the same transaction as those entries. Minor units only.';

CREATE UNIQUE INDEX uq_accounts_one_funding_per_currency
    ON accounts (currency)
    WHERE kind = 'SYSTEM_FUNDING';

CREATE INDEX idx_accounts_owner_created
    ON accounts (owner_id, created_at DESC);

CREATE TABLE transfers (
    id UUID PRIMARY KEY,
    kind VARCHAR(16) NOT NULL,
    requester_id UUID NOT NULL REFERENCES app_users (id),
    source_account_id UUID NOT NULL REFERENCES accounts (id),
    destination_account_id UUID NOT NULL REFERENCES accounts (id),
    currency VARCHAR(3) NOT NULL,
    amount_minor BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT transfers_kind_chk CHECK (kind IN ('TRANSFER', 'DEPOSIT')),
    CONSTRAINT transfers_status_chk CHECK (status IN ('POSTED')),
    CONSTRAINT transfers_amount_positive CHECK (amount_minor > 0),
    CONSTRAINT transfers_distinct_accounts CHECK (source_account_id <> destination_account_id),
    CONSTRAINT transfers_currency_iso CHECK (currency ~ '^[A-Z]{3}$')
);

CREATE INDEX idx_transfers_source ON transfers (source_account_id);
CREATE INDEX idx_transfers_destination ON transfers (destination_account_id);
CREATE INDEX idx_transfers_requester ON transfers (requester_id);

CREATE TABLE ledger_entries (
    id UUID PRIMARY KEY,
    transfer_id UUID NOT NULL REFERENCES transfers (id),
    account_id UUID NOT NULL REFERENCES accounts (id),
    direction VARCHAR(8) NOT NULL,
    amount_minor BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ledger_entries_direction_chk CHECK (direction IN ('DEBIT', 'CREDIT')),
    CONSTRAINT ledger_entries_amount_positive CHECK (amount_minor > 0),
    CONSTRAINT ledger_entries_currency_iso CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT uq_ledger_entries_transfer_direction UNIQUE (transfer_id, direction)
);

COMMENT ON TABLE ledger_entries IS
    'Double-entry lines. A posted transfer has one DEBIT and one CREDIT for the same minor-unit amount.';

CREATE INDEX idx_ledger_entries_account_created
    ON ledger_entries (account_id, created_at DESC, id DESC);

CREATE TABLE idempotency_keys (
    id UUID PRIMARY KEY,
    requester_id UUID NOT NULL REFERENCES app_users (id),
    idempotency_key VARCHAR(200) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    transfer_id UUID REFERENCES transfers (id),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_idempotency_requester_key UNIQUE (requester_id, idempotency_key),
    CONSTRAINT idempotency_hash_hex CHECK (request_hash ~ '^[0-9a-f]{64}$')
);

CREATE INDEX idx_idempotency_transfer ON idempotency_keys (transfer_id);

-- Runs at commit. The functions read the current row, not the NEW image from the
-- statement that queued the trigger, so an update later in the same transaction is visible.
CREATE OR REPLACE FUNCTION assert_transfer_balanced(target uuid)
RETURNS void
LANGUAGE plpgsql
SET search_path = public
AS $$
DECLARE
    entry_count integer;
    debit_sum bigint;
    credit_sum bigint;
BEGIN
    SELECT COUNT(*),
           COALESCE(SUM(amount_minor) FILTER (WHERE direction = 'DEBIT'), 0),
           COALESCE(SUM(amount_minor) FILTER (WHERE direction = 'CREDIT'), 0)
      INTO entry_count, debit_sum, credit_sum
      FROM ledger_entries
     WHERE transfer_id = target;

    IF entry_count <> 2 OR debit_sum <> credit_sum THEN
        RAISE EXCEPTION 'unbalanced ledger transfer %', target
            USING ERRCODE = '23514';
    END IF;
END;
$$;

CREATE OR REPLACE FUNCTION assert_transfer_row_balanced()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = public
AS $$
BEGIN
    PERFORM assert_transfer_balanced(NEW.id);
    RETURN NULL;
END;
$$;

CREATE OR REPLACE FUNCTION assert_ledger_row_balanced()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = public
AS $$
DECLARE
    target uuid;
BEGIN
    IF TG_OP = 'DELETE' THEN
        target := OLD.transfer_id;
    ELSE
        target := NEW.transfer_id;
    END IF;
    PERFORM assert_transfer_balanced(target);
    RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER trg_transfers_balanced
    AFTER INSERT OR UPDATE ON transfers
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW
    EXECUTE FUNCTION assert_transfer_row_balanced();

CREATE CONSTRAINT TRIGGER trg_ledger_entries_balanced
    AFTER INSERT OR UPDATE OR DELETE ON ledger_entries
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW
    EXECUTE FUNCTION assert_ledger_row_balanced();

-- PostgreSQL cannot mark a CHECK constraint DEFERRABLE. This trigger allows transfer_id
-- to be filled in after the idempotency claim is inserted, and rejects the commit if it is still null.
CREATE OR REPLACE FUNCTION assert_idempotency_transfer_present()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = public
AS $$
DECLARE
    current_transfer uuid;
    current_key varchar(200);
BEGIN
    SELECT transfer_id, idempotency_key
      INTO current_transfer, current_key
      FROM idempotency_keys
     WHERE id = NEW.id;

    IF current_transfer IS NULL THEN
        RAISE EXCEPTION 'idempotency key % has no transfer', current_key
            USING ERRCODE = '23514';
    END IF;
    RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER trg_idempotency_transfer_present
    AFTER INSERT OR UPDATE ON idempotency_keys
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW
    EXECUTE FUNCTION assert_idempotency_transfer_present();
