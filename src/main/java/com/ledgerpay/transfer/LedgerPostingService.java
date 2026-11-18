package com.ledgerpay.transfer;

import com.ledgerpay.account.Account;
import com.ledgerpay.account.AccountKind;
import com.ledgerpay.account.AccountRepository;
import com.ledgerpay.error.ApiException;
import com.ledgerpay.error.ErrorCode;
import com.ledgerpay.idempotency.IdempotencyRecord;
import com.ledgerpay.ledger.EntryDirection;
import com.ledgerpay.ledger.LedgerEntry;
import com.ledgerpay.money.Money;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PessimisticLockException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The only write path for balances and ledger lines.
 *
 * <p>Isolation is READ COMMITTED. Correctness under concurrent spending comes from row locks,
 * not from a serializable isolation level. Both accounts are locked with {@code SELECT FOR UPDATE}
 * in ascending account-id order before any balance or ledger write. That order is the deadlock
 * rule for opposing transfers.
 *
 * <p>The idempotency key is inserted and flushed before those locks. A second request with the
 * same key waits on the unique index, then fails its insert and replays the committed transfer
 * instead of attempting a second debit.
 *
 * <p>The method is one transaction. A runtime exception, a constraint failure, or a failed
 * commit rolls back the idempotency claim, the transfer, both ledger lines, and both balance
 * updates together. Deferred database triggers also reject a commit that is not a balanced pair.
 */
@Service
public class LedgerPostingService {

    private static final Logger log = LoggerFactory.getLogger(LedgerPostingService.class);
    private static final Map<String, Object> LOCK_HINTS = Map.of("jakarta.persistence.lock.timeout", 5000);

    private final EntityManager entityManager;
    private final AccountRepository accounts;
    private final Clock clock;

    public LedgerPostingService(EntityManager entityManager, AccountRepository accounts, Clock clock) {
        this.entityManager = entityManager;
        this.accounts = accounts;
        this.clock = clock;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED, timeout = 15)
    public PostedTransfer post(PostingCommand command) {
        try {
            entityManager.createNativeQuery("SET LOCAL lock_timeout = '5s'").executeUpdate();
            Instant now = clock.instant();
            IdempotencyRecord claim = IdempotencyRecord.claim(
                    command.requesterId(), command.idempotencyKey(), command.requestHash(), now);
            entityManager.persist(claim);
            entityManager.flush();

            UUID sourceId = command.sourceAccountId();
            UUID destinationId = command.destinationAccountId();
            if (command.kind() == TransferKind.DEPOSIT) {
                sourceId = fundingAccountId(command.money());
            }
            Map<UUID, Account> locked = lockInOrder(sourceId, destinationId);
            Account source = required(locked, sourceId);
            Account destination = required(locked, destinationId);
            validate(command, source, destination);

            Transfer transfer = Transfer.posted(
                    command.requesterId(),
                    command.kind(),
                    source.getId(),
                    destination.getId(),
                    command.money().currency().name(),
                    command.money().minorUnits(),
                    now);
            entityManager.persist(transfer);

            LedgerEntry debit = LedgerEntry.of(
                    transfer.getId(),
                    source.getId(),
                    EntryDirection.DEBIT,
                    command.money().minorUnits(),
                    command.money().currency().name(),
                    now);
            LedgerEntry credit = LedgerEntry.of(
                    transfer.getId(),
                    destination.getId(),
                    EntryDirection.CREDIT,
                    command.money().minorUnits(),
                    command.money().currency().name(),
                    now);
            entityManager.persist(debit);
            entityManager.persist(credit);

            source.applyDebit(command.money().minorUnits());
            destination.applyCredit(command.money().minorUnits());
            claim.assignTransfer(transfer.getId());
            entityManager.flush();

            log.info("Posted {} {} of {} {}", transfer.getKind(), transfer.getId(),
                    command.money().minorUnits(), command.money().currency());
            return new PostedTransfer(transfer, List.of(debit, credit));
        } catch (PessimisticLockException | PessimisticLockingFailureException | QueryTimeoutException exception) {
            throw new ApiException(
                    ErrorCode.LOCK_TIMEOUT,
                    "The accounts are busy. Retry with the same Idempotency-Key.");
        }
    }

    private UUID fundingAccountId(Money money) {
        return accounts.findByKindAndCurrency(AccountKind.SYSTEM_FUNDING, money.currency().name())
                .map(Account::getId)
                .orElseThrow(() -> new IllegalStateException(
                        "Funding account is missing for " + money.currency().name()));
    }

    private Map<UUID, Account> lockInOrder(UUID first, UUID second) {
        List<UUID> ordered = new ArrayList<>(2);
        if (first.compareTo(second) <= 0) {
            ordered.add(first);
            ordered.add(second);
        } else {
            ordered.add(second);
            ordered.add(first);
        }
        Map<UUID, Account> locked = new LinkedHashMap<>();
        for (UUID id : ordered) {
            Account account = entityManager.find(Account.class, id, LockModeType.PESSIMISTIC_WRITE, LOCK_HINTS);
            if (account == null) {
                throw new ApiException(ErrorCode.ACCOUNT_NOT_FOUND, "Account " + id + " does not exist.");
            }
            locked.put(id, account);
        }
        return locked;
    }

    private static Account required(Map<UUID, Account> locked, UUID id) {
        Account account = locked.get(id);
        if (account == null) {
            throw new ApiException(ErrorCode.ACCOUNT_NOT_FOUND, "Account " + id + " does not exist.");
        }
        return account;
    }

    private static void validate(PostingCommand command, Account source, Account destination) {
        if (source.getId().equals(destination.getId())) {
            throw new ApiException(ErrorCode.SAME_ACCOUNT, "Source and destination accounts must differ.");
        }
        String currency = command.money().currency().name();
        if (!source.getCurrency().equals(currency) || !destination.getCurrency().equals(currency)) {
            throw new ApiException(
                    ErrorCode.CURRENCY_MISMATCH,
                    "Both accounts and the amount must use the same currency.");
        }
        if (command.kind() == TransferKind.TRANSFER) {
            validateCustomerTransfer(command, source, destination);
        } else {
            validateDeposit(command, source, destination);
        }
    }

    private static void validateCustomerTransfer(PostingCommand command, Account source, Account destination) {
        if (source.getKind() != AccountKind.CUSTOMER || destination.getKind() != AccountKind.CUSTOMER) {
            throw new ApiException(ErrorCode.ACCOUNT_NOT_FOUND, "Account is not a customer account.");
        }
        if (!source.getOwnerId().equals(command.requesterId())) {
            throw new ApiException(ErrorCode.OWNERSHIP_VIOLATION, "You can only debit your own account.");
        }
        requireActive(source);
        requireActive(destination);
        if (source.getBalanceMinor() < command.money().minorUnits()) {
            throw new ApiException(ErrorCode.INSUFFICIENT_FUNDS, "Account balance is insufficient for this transfer.");
        }
    }

    private static void validateDeposit(PostingCommand command, Account source, Account destination) {
        if (source.getKind() != AccountKind.SYSTEM_FUNDING || destination.getKind() != AccountKind.CUSTOMER) {
            throw new ApiException(ErrorCode.ACCOUNT_NOT_FOUND, "Synthetic deposit destination is not valid.");
        }
        if (!destination.getOwnerId().equals(command.requesterId())) {
            throw new ApiException(ErrorCode.OWNERSHIP_VIOLATION, "You can only fund your own account.");
        }
        requireActive(destination);
    }

    private static void requireActive(Account account) {
        if (!account.getStatus().canMoveFunds()) {
            throw new ApiException(
                    ErrorCode.ACCOUNT_NOT_ACTIVE,
                    "Account " + account.getId() + " is " + account.getStatus() + " and cannot move funds.");
        }
    }
}
