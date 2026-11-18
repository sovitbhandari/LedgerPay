package com.ledgerpay.transfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ledgerpay.account.Account;
import com.ledgerpay.account.AccountRepository;
import com.ledgerpay.error.ApiException;
import com.ledgerpay.error.ErrorCode;
import com.ledgerpay.ledger.EntryDirection;
import com.ledgerpay.ledger.LedgerEntry;
import com.ledgerpay.money.LedgerCurrency;
import com.ledgerpay.support.PostgresIntegrationTest;
import com.ledgerpay.user.AppUser;
import com.ledgerpay.user.AppUserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

class PostgresTransactionRollbackTest extends PostgresIntegrationTest {

    @Autowired
    private TransactionTemplate transactionTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private AccountRepository accounts;

    @Autowired
    private TransferService transfers;

    @Test
    void unbalancedLedgerInsertRollsBackWithTheTransfer() {
        UUID owner = users.save(AppUser.register(
                        "rollback-" + UUID.randomUUID() + "@example.com", "not-used-for-login", Instant.now()))
                .getId();
        UUID source = accounts.save(Account.customer(owner, "Source", LedgerCurrency.USD, Instant.now())).getId();
        UUID destination = accounts.save(Account.customer(owner, "Destination", LedgerCurrency.USD, Instant.now())).getId();
        jdbc.update("update accounts set balance_minor = 1000 where id = ?", source);
        try {
            Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(() ->
                    transactionTemplate.executeWithoutResult(status -> {
                        Transfer transfer = Transfer.posted(
                                owner, TransferKind.TRANSFER, source, destination, "USD", 100, Instant.now());
                        entityManager.persist(transfer);
                        entityManager.persist(LedgerEntry.of(
                                transfer.getId(), source, EntryDirection.DEBIT, 100, "USD", Instant.now()));
                        entityManager.flush();
                    }));
            assertThat(thrown).isNotNull();
            assertThat(rootMessage(thrown)).contains("unbalanced ledger transfer");
            assertThat(jdbc.queryForObject(
                            "select count(*) from ledger_entries where account_id = ?", Integer.class, source))
                    .isZero();
            assertThat(jdbc.queryForObject("select balance_minor from accounts where id = ?", Long.class, source))
                    .isEqualTo(1000);
        } finally {
            jdbc.update("update accounts set balance_minor = 0 where id = ?", source);
        }
    }

    @Test
    void negativeCustomerBalanceIsRejectedAndLeavesTheRowUnchanged() {
        UUID owner = users.save(AppUser.register(
                        "check-" + UUID.randomUUID() + "@example.com", "not-used-for-login", Instant.now()))
                .getId();
        UUID accountId = accounts.save(Account.customer(owner, "Checked", LedgerCurrency.USD, Instant.now())).getId();
        jdbc.update("update accounts set balance_minor = 400 where id = ?", accountId);

        Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(() ->
                transactionTemplate.executeWithoutResult(status -> entityManager
                        .createNativeQuery("update accounts set balance_minor = -1 where id = :id")
                        .setParameter("id", accountId)
                        .executeUpdate()));
        assertThat(thrown).isNotNull();
        assertThat(rootMessage(thrown)).contains("accounts_customer_balance_non_negative");
        assertThat(jdbc.queryForObject("select balance_minor from accounts where id = ?", Long.class, accountId))
                .isEqualTo(400);
        jdbc.update("update accounts set balance_minor = 0 where id = ?", accountId);
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return String.valueOf(current.getMessage());
    }

    @Test
    void failedTransferDoesNotLeaveAPartialPosting() {
        UUID owner = users.save(AppUser.register(
                        "nofunds-" + UUID.randomUUID() + "@example.com", "not-used-for-login", Instant.now()))
                .getId();
        UUID other = users.save(AppUser.register(
                        "nofunds-other-" + UUID.randomUUID() + "@example.com", "not-used-for-login", Instant.now()))
                .getId();
        UUID source = accounts.save(Account.customer(owner, "Empty", LedgerCurrency.GBP, Instant.now())).getId();
        UUID destination = accounts.save(Account.customer(other, "Other", LedgerCurrency.GBP, Instant.now())).getId();
        transfers.deposit(owner, source, "tiny-" + UUID.randomUUID(), "1.00", "GBP");
        long entries = jdbc.queryForObject("select count(*) from ledger_entries", Long.class);

        assertThatThrownBy(() -> transfers.create(
                        owner,
                        "overdraft-" + UUID.randomUUID(),
                        new CreateTransferRequest(source, destination, "GBP", "5.00")))
                .isInstanceOfSatisfying(ApiException.class, error ->
                        assertThat(error.errorCode()).isEqualTo(ErrorCode.INSUFFICIENT_FUNDS));

        assertThat(jdbc.queryForObject("select balance_minor from accounts where id = ?", Long.class, source))
                .isEqualTo(100);
        assertThat(jdbc.queryForObject("select count(*) from ledger_entries", Long.class)).isEqualTo(entries);
    }
}
