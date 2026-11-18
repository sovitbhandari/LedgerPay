package com.ledgerpay.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "app_users")
public class AppUser {

    @Id
    private UUID id;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "system_account", nullable = false)
    private boolean systemAccount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AppUser() {
    }

    private AppUser(UUID id, String email, String passwordHash, boolean systemAccount, Instant createdAt) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.systemAccount = systemAccount;
        this.createdAt = createdAt;
    }

    public static AppUser register(String email, String passwordHash, Instant createdAt) {
        return new AppUser(UUID.randomUUID(), email, passwordHash, false, createdAt);
    }

    public static AppUser system(String email, String passwordHash, Instant createdAt) {
        return new AppUser(UUID.randomUUID(), email, passwordHash, true, createdAt);
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public boolean isSystemAccount() {
        return systemAccount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
