package com.ledgerpay.account;

import com.ledgerpay.error.ApiException;
import com.ledgerpay.error.ErrorCode;
import com.ledgerpay.ledger.LedgerEntryRepository;
import com.ledgerpay.ledger.LedgerPageResponse;
import com.ledgerpay.money.LedgerCurrency;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    private static final Map<String, Object> LOCK_HINTS = Map.of("jakarta.persistence.lock.timeout", 5000);

    private final AccountRepository accounts;
    private final LedgerEntryRepository entries;
    private final EntityManager entityManager;
    private final Clock clock;

    public AccountService(
            AccountRepository accounts,
            LedgerEntryRepository entries,
            EntityManager entityManager,
            Clock clock) {
        this.accounts = accounts;
        this.entries = entries;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    @Transactional
    public AccountResponse create(UUID ownerId, String name, String currencyCode) {
        LedgerCurrency currency = LedgerCurrency.require(currencyCode);
        Account account = accounts.saveAndFlush(
                Account.customer(ownerId, cleanName(name), currency, clock.instant()));
        return AccountMapper.toResponse(account);
    }

    @Transactional(readOnly = true)
    public AccountPageResponse list(UUID ownerId, int page, int size) {
        Page<Account> result = accounts.findByOwnerIdAndKind(ownerId, AccountKind.CUSTOMER, PageRequests.of(page, size));
        return new AccountPageResponse(
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.getContent().stream().map(AccountMapper::toResponse).toList());
    }

    @Transactional(readOnly = true)
    public BalanceResponse balance(UUID ownerId, UUID accountId) {
        return AccountMapper.toBalance(ownedCustomerAccount(ownerId, accountId));
    }

    @Transactional(readOnly = true)
    public LedgerPageResponse ledger(UUID ownerId, UUID accountId, int page, int size) {
        Account account = ownedCustomerAccount(ownerId, accountId);
        return AccountMapper.toLedgerPage(account, entries.findByAccountId(accountId, PageRequests.of(page, size)));
    }

    @Transactional
    public AccountResponse changeStatus(UUID ownerId, UUID accountId, AccountStatus status) {
        Account account = entityManager.find(Account.class, accountId, LockModeType.PESSIMISTIC_WRITE, LOCK_HINTS);
        if (account == null || account.getKind() != AccountKind.CUSTOMER) {
            throw new ApiException(ErrorCode.ACCOUNT_NOT_FOUND, "Account " + accountId + " does not exist.");
        }
        if (!account.getOwnerId().equals(ownerId)) {
            throw new ApiException(ErrorCode.OWNERSHIP_VIOLATION, "You can only change your own account.");
        }
        account.changeStatus(status);
        entityManager.flush();
        return AccountMapper.toResponse(account);
    }

    public Account ownedCustomerAccount(UUID ownerId, UUID accountId) {
        Account account = accounts.findById(accountId)
                .filter(candidate -> candidate.getKind() == AccountKind.CUSTOMER)
                .orElseThrow(() -> new ApiException(
                        ErrorCode.ACCOUNT_NOT_FOUND, "Account " + accountId + " does not exist."));
        if (!account.getOwnerId().equals(ownerId)) {
            throw new ApiException(ErrorCode.OWNERSHIP_VIOLATION, "You can only access your own account.");
        }
        return account;
    }

    static String cleanName(String name) {
        if (name == null) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Account name is required.");
        }
        String trimmed = name.trim();
        if (trimmed.isEmpty() || trimmed.length() > 80) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Account name must be between 1 and 80 characters.");
        }
        if (trimmed.chars().anyMatch(Character::isISOControl)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Account name contains unsupported characters.");
        }
        return trimmed;
    }
}
