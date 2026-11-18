package com.ledgerpay.ledger;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ledger_entries")
public class LedgerEntry {

    @Id
    private UUID id;

    @Column(name = "transfer_id", nullable = false, updatable = false)
    private UUID transferId;

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private EntryDirection direction;

    @Column(name = "amount_minor", nullable = false, updatable = false)
    private long amountMinor;

    @Column(nullable = false, length = 3, updatable = false)
    private String currency;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected LedgerEntry() {
    }

    private LedgerEntry(
            UUID transferId,
            UUID accountId,
            EntryDirection direction,
            long amountMinor,
            String currency,
            Instant createdAt) {
        this.id = UUID.randomUUID();
        this.transferId = transferId;
        this.accountId = accountId;
        this.direction = direction;
        this.amountMinor = amountMinor;
        this.currency = currency;
        this.createdAt = createdAt;
    }

    public static LedgerEntry of(
            UUID transferId,
            UUID accountId,
            EntryDirection direction,
            long amountMinor,
            String currency,
            Instant createdAt) {
        return new LedgerEntry(transferId, accountId, direction, amountMinor, currency, createdAt);
    }

    public UUID getId() {
        return id;
    }

    public UUID getTransferId() {
        return transferId;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public EntryDirection getDirection() {
        return direction;
    }

    public long getAmountMinor() {
        return amountMinor;
    }

    public String getCurrency() {
        return currency;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
