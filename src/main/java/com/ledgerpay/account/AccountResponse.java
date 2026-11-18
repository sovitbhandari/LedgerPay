package com.ledgerpay.account;

import java.time.Instant;
import java.util.UUID;

public record AccountResponse(
        UUID id,
        String name,
        String currency,
        int scale,
        String status,
        long balanceMinor,
        String balance,
        Instant createdAt) {
}
