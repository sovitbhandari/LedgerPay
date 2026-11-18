package com.ledgerpay.idempotency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "idempotency_keys")
public class IdempotencyRecord {

    @Id
    private UUID id;

    @Column(name = "requester_id", nullable = false, updatable = false)
    private UUID requesterId;

    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 200)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, updatable = false, length = 64)
    private String requestHash;

    @Column(name = "transfer_id")
    private UUID transferId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected IdempotencyRecord() {
    }

    private IdempotencyRecord(UUID requesterId, String idempotencyKey, String requestHash, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.requesterId = requesterId;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.createdAt = createdAt;
    }

    public static IdempotencyRecord claim(
            UUID requesterId, String idempotencyKey, String requestHash, Instant createdAt) {
        return new IdempotencyRecord(requesterId, idempotencyKey, requestHash, createdAt);
    }

    public void assignTransfer(UUID postedTransferId) {
        if (postedTransferId == null) {
            throw new IllegalArgumentException("transfer id is required");
        }
        this.transferId = postedTransferId;
    }

    public UUID getId() {
        return id;
    }

    public UUID getRequesterId() {
        return requesterId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public UUID getTransferId() {
        return transferId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
