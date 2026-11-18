package com.ledgerpay.support;

import java.time.Duration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * One PostgreSQL 16 container for the suite. Testcontainers starts it; Spring's context cache
 * reuses the same JDBC URL so the integration tests share a migrated database.
 */
public final class SharedPostgres {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("ledgerpay")
            .withUsername("ledgerpay")
            .withPassword("ledgerpay")
            .withStartupTimeout(Duration.ofMinutes(3));

    private SharedPostgres() {
    }

    public static void register(DynamicPropertyRegistry registry) {
        if (!POSTGRES.isRunning()) {
            POSTGRES.start();
        }
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
