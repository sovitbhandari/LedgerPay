package com.ledgerpay.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ledgerpay.jwt")
public record JwtProperties(String secret, Duration ttl) {
}
