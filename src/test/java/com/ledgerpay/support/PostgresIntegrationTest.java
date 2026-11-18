package com.ledgerpay.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
@AutoConfigureMockMvc
public abstract class PostgresIntegrationTest {

    @DynamicPropertySource
    static void registerDatasource(DynamicPropertyRegistry registry) {
        SharedPostgres.register(registry);
    }
}
