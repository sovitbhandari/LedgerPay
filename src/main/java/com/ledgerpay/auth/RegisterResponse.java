package com.ledgerpay.auth;

import java.util.UUID;

public record RegisterResponse(
        UUID userId,
        String email,
        String tokenType,
        String accessToken,
        long expiresInSeconds) {
}
