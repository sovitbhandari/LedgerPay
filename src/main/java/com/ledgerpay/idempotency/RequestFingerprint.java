package com.ledgerpay.idempotency;

import com.ledgerpay.money.Money;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Canonical request identity. Equivalent amounts such as 10, 10.0, and 10.00 share a hash
 * because the hash uses minor units, not the original text.
 */
public final class RequestFingerprint {

    private RequestFingerprint() {
    }

    public static String transfer(UUID sourceAccountId, UUID destinationAccountId, Money money) {
        return sha256("TRANSFER|" + sourceAccountId + "|" + destinationAccountId + "|"
                + money.currency().name() + "|" + money.minorUnits());
    }

    public static String deposit(UUID accountId, Money money) {
        return sha256("DEPOSIT|" + accountId + "|" + money.currency().name() + "|" + money.minorUnits());
    }

    private static String sha256(String canonical) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
