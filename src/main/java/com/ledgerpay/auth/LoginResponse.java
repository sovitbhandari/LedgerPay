package com.ledgerpay.auth;

public record LoginResponse(String tokenType, String accessToken, long expiresInSeconds) {
}
