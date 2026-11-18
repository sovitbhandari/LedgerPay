package com.ledgerpay.transfer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transfers")
public class Transfer {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16, updatable = false)
    private TransferKind kind;

    @Column(name = "requester_id", nullable = false, updatable = false)
    private UUID requesterId;

    @Column(name = "source_account_id", nullable = false, updatable = false)
    private UUID sourceAccountId;

    @Column(name = "destination_account_id", nullable = false, updatable = false)
    private UUID destinationAccountId;

    @Column(nullable = false, length = 3, updatable = false)
    private String currency;

    @Column(name = "amount_minor", nullable = false, updatable = false)
    private long amountMinor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16, updatable = false)
    private TransferStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Transfer() {
    }

    private Transfer(
            UUID requesterId,
            TransferKind kind,
            UUID sourceAccountId,
            UUID destinationAccountId,
            String currency,
            long amountMinor,
            Instant createdAt) {
        this.id = UUID.randomUUID();
        this.requesterId = requesterId;
        this.kind = kind;
        this.sourceAccountId = sourceAccountId;
        this.destinationAccountId = destinationAccountId;
        this.currency = currency;
        this.amountMinor = amountMinor;
        this.status = TransferStatus.POSTED;
        this.createdAt = createdAt;
    }

    public static Transfer posted(
            UUID requesterId,
            TransferKind kind,
            UUID sourceAccountId,
            UUID destinationAccountId,
            String currency,
            long amountMinor,
            Instant createdAt) {
        return new Transfer(
                requesterId, kind, sourceAccountId, destinationAccountId, currency, amountMinor, createdAt);
    }

    public UUID getId() {
        return id;
    }

    public TransferKind getKind() {
        return kind;
    }

    public UUID getRequesterId() {
        return requesterId;
    }

    public UUID getSourceAccountId() {
        return sourceAccountId;
    }

    public UUID getDestinationAccountId() {
        return destinationAccountId;
    }

    public String getCurrency() {
        return currency;
    }

    public long getAmountMinor() {
        return amountMinor;
    }

    public TransferStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
