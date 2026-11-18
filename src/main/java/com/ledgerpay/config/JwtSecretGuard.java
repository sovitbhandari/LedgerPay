package com.ledgerpay.config;

import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;

@Component
public class JwtSecretGuard {

    public JwtSecretGuard(JwtProperties properties) {
        if (properties.secret() == null || properties.secret().getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("ledgerpay.jwt.secret must be at least 32 UTF-8 bytes");
        }
        if (properties.ttl() == null || properties.ttl().toMinutes() < 5) {
            throw new IllegalStateException("ledgerpay.jwt.ttl must be at least 5 minutes");
        }
    }
}
