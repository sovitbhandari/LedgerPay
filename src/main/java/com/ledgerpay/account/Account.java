package com.ledgerpay.account;

import com.ledgerpay.error.ApiException;
import com.ledgerpay.error.ErrorCode;
import com.ledgerpay.money.LedgerCurrency;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "accounts")
public class Account {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AccountStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AccountKind kind;

    @Column(name = "balance_minor", nullable = false)
    private long balanceMinor;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Account() {
    }

    private Account(
            UUID id,
            UUID ownerId,
            String name,
            String currency,
            AccountStatus status,
            AccountKind kind,
            long balanceMinor,
            Instant createdAt) {
        this.id = id;
        this.ownerId = ownerId;
        this.name = name;
        this.currency = currency;
        this.status = status;
        this.kind = kind;
        this.balanceMinor = balanceMinor;
        this.createdAt = createdAt;
    }

    public static Account customer(UUID ownerId, String name, LedgerCurrency currency, Instant createdAt) {
        return new Account(
                UUID.randomUUID(),
                ownerId,
                name,
                currency.name(),
                AccountStatus.ACTIVE,
                AccountKind.CUSTOMER,
                0L,
                createdAt);
    }

    public static Account funding(UUID ownerId, LedgerCurrency currency, Instant createdAt) {
        return new Account(
                UUID.randomUUID(),
                ownerId,
                currency.name() + " funding",
                currency.name(),
                AccountStatus.ACTIVE,
                AccountKind.SYSTEM_FUNDING,
                0L,
                createdAt);
    }

    public void applyDebit(long amountMinor) {
        requirePositive(amountMinor);
        if (kind == AccountKind.CUSTOMER && balanceMinor < amountMinor) {
            throw new ApiException(ErrorCode.INSUFFICIENT_FUNDS, "Account balance is insufficient for this debit.");
        }
        if (balanceMinor < Long.MIN_VALUE + amountMinor) {
            throw new ApiException(ErrorCode.INVALID_AMOUNT, "Debit would overflow the balance.");
        }
        balanceMinor -= amountMinor;
    }

    public void applyCredit(long amountMinor) {
        requirePositive(amountMinor);
        if (balanceMinor > Long.MAX_VALUE - amountMinor) {
            throw new ApiException(ErrorCode.INVALID_AMOUNT, "Credit would overflow the balance.");
        }
        balanceMinor += amountMinor;
    }

    public void changeStatus(AccountStatus next) {
        if (kind != AccountKind.CUSTOMER) {
            throw new ApiException(ErrorCode.INVALID_STATUS_TRANSITION, "System funding accounts stay active.");
        }
        this.status = status.transitionTo(next);
    }

    public UUID getId() {
        return id;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public String getName() {
        return name;
    }

    public String getCurrency() {
        return currency;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public AccountKind getKind() {
        return kind;
    }

    public long getBalanceMinor() {
        return balanceMinor;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    private static void requirePositive(long amountMinor) {
        if (amountMinor <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
    }
}
