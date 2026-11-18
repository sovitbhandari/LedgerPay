package com.ledgerpay;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

// Boot's default in-memory user is not used. Authentication is the JWT filter.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class LedgerPayApplication {

    public static void main(String[] args) {
        SpringApplication.run(LedgerPayApplication.class, args);
    }
}
