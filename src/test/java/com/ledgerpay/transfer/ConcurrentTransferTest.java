package com.ledgerpay.transfer;

import static org.assertj.core.api.Assertions.assertThat;

import com.ledgerpay.account.Account;
import com.ledgerpay.account.AccountRepository;
import com.ledgerpay.error.ApiException;
import com.ledgerpay.error.ErrorCode;
import com.ledgerpay.money.LedgerCurrency;
import com.ledgerpay.support.PostgresIntegrationTest;
import com.ledgerpay.user.AppUser;
import com.ledgerpay.user.AppUserRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class ConcurrentTransferTest extends PostgresIntegrationTest {

    @Autowired
    private TransferService transfers;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private AccountRepository accounts;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void simultaneousSpendsCannotDriveABalanceNegative() throws Exception {
        UUID owner = user("spend");
        UUID recipient = user("recv");
        UUID source = customer(owner, "USD", "Spend");
        UUID destination = customer(recipient, "USD", "Receive");
        transfers.deposit(owner, source, "seed-" + UUID.randomUUID(), "5.00", "USD");

        int threads = 16;
        AtomicInteger success = new AtomicInteger();
        AtomicInteger insufficient = new AtomicInteger();
        List<Throwable> unexpected = Collections.synchronizedList(new ArrayList<>());
        runTogether(threads, index -> {
            try {
                transfers.create(
                        owner,
                        "race-" + index + "-" + UUID.randomUUID(),
                        new CreateTransferRequest(source, destination, "USD", "1.00"));
                success.incrementAndGet();
            } catch (ApiException exception) {
                if (exception.errorCode() == ErrorCode.INSUFFICIENT_FUNDS) {
                    insufficient.incrementAndGet();
                } else {
                    unexpected.add(exception);
                }
            }
        }, unexpected);

        assertThat(unexpected).isEmpty();
        assertThat(success.get()).isEqualTo(5);
        assertThat(insufficient.get()).isEqualTo(11);
        assertThat(balance(source)).isZero();
        assertThat(balance(destination)).isEqualTo(500);
        assertLedgerInvariants();
    }

    @Test
    void opposingTransfersDoNotDeadlock() throws Exception {
        UUID alice = user("left");
        UUID bob = user("right");
        UUID left = customer(alice, "USD", "Left");
        UUID right = customer(bob, "USD", "Right");
        transfers.deposit(alice, left, "left-" + UUID.randomUUID(), "20.00", "USD");
        transfers.deposit(bob, right, "right-" + UUID.randomUUID(), "20.00", "USD");

        int threads = 20;
        AtomicInteger success = new AtomicInteger();
        List<Throwable> unexpected = runTogether(threads, index -> {
            boolean leftToRight = index % 2 == 0;
            UUID requester = leftToRight ? alice : bob;
            UUID source = leftToRight ? left : right;
            UUID destination = leftToRight ? right : left;
            transfers.create(
                    requester,
                    "cross-" + index + "-" + UUID.randomUUID(),
                    new CreateTransferRequest(source, destination, "USD", "1.00"));
            success.incrementAndGet();
        }, new ArrayList<>());

        assertThat(unexpected).isEmpty();
        assertThat(success.get()).isEqualTo(threads);
        assertThat(balance(left)).isEqualTo(2000);
        assertThat(balance(right)).isEqualTo(2000);
        assertLedgerInvariants();
    }

    @Test
    void duplicateKeysUnderConcurrencyCreateOneTransfer() throws Exception {
        UUID owner = user("idem");
        UUID recipient = user("idem-recv");
        UUID source = customer(owner, "EUR", "Idem source");
        UUID destination = customer(recipient, "EUR", "Idem dest");
        transfers.deposit(owner, source, "idem-seed-" + UUID.randomUUID(), "30.00", "EUR");
        String key = "same-key-" + UUID.randomUUID();
        List<UUID> ids = Collections.synchronizedList(new ArrayList<>());
        List<Throwable> unexpected = runTogether(8, index -> {
            TransferResponse response = transfers.create(
                    owner, key, new CreateTransferRequest(source, destination, "EUR", "4.00"));
            ids.add(response.id());
        }, new ArrayList<>());

        assertThat(unexpected).isEmpty();
        assertThat(ids).hasSize(8);
        assertThat(ids).containsOnly(ids.getFirst());
        Integer rows = jdbc.queryForObject(
                "select count(*) from idempotency_keys where idempotency_key = ?", Integer.class, key);
        assertThat(rows).isEqualTo(1);
        assertThat(balance(source)).isEqualTo(2600);
        assertLedgerInvariants();
    }

    private List<Throwable> runTogether(int threads, ConcurrentWork work, List<Throwable> unexpected) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Throwable> failures = unexpected;
        try {
            for (int index = 0; index < threads; index++) {
                int current = index;
                pool.submit(() -> {
                    ready.countDown();
                    try {
                        if (!start.await(10, TimeUnit.SECONDS)) {
                            failures.add(new IllegalStateException("start latch timed out"));
                            return;
                        }
                        work.run(current);
                    } catch (Throwable throwable) {
                        failures.add(throwable);
                    }
                });
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return failures;
        } finally {
            pool.shutdown();
            assertThat(pool.awaitTermination(45, TimeUnit.SECONDS)).isTrue();
        }
    }

    private void assertLedgerInvariants() {
        assertThat(jdbc.queryForObject("select coalesce(sum(balance_minor), 0) from accounts", Long.class)).isZero();
        assertThat(jdbc.queryForObject(
                        """
                        select count(*) from (
                            select a.id
                              from accounts a
                              left join ledger_entries e on e.account_id = a.id
                             group by a.id, a.balance_minor
                            having a.balance_minor <> coalesce(sum(
                                case when e.direction = 'CREDIT' then e.amount_minor
                                     when e.direction = 'DEBIT' then -e.amount_minor
                                     else 0 end), 0)
                        ) mismatches
                        """,
                        Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject(
                        """
                        select count(*) from (
                            select transfer_id
                              from ledger_entries
                             group by transfer_id
                            having count(*) <> 2
                                or coalesce(sum(amount_minor) filter (where direction = 'DEBIT'), 0)
                                 <> coalesce(sum(amount_minor) filter (where direction = 'CREDIT'), 0)
                        ) unbalanced
                        """,
                        Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject(
                        "select count(*) from accounts where kind = 'CUSTOMER' and balance_minor < 0", Integer.class))
                .isZero();
    }

    private UUID user(String prefix) {
        return users.save(AppUser.register(
                        prefix + "-" + UUID.randomUUID() + "@example.com",
                        "not-used-for-login",
                        Instant.now()))
                .getId();
    }

    private UUID customer(UUID ownerId, String currency, String name) {
        return accounts.save(Account.customer(ownerId, name, LedgerCurrency.require(currency), Instant.now()))
                .getId();
    }

    private long balance(UUID accountId) {
        return jdbc.queryForObject("select balance_minor from accounts where id = ?", Long.class, accountId);
    }

    @FunctionalInterface
    private interface ConcurrentWork {
        void run(int index) throws Exception;
    }
}
