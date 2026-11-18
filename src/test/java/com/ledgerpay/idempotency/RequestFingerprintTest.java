package com.ledgerpay.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import com.ledgerpay.money.Money;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RequestFingerprintTest {

    @Test
    void equivalentAmountsShareAHashAndDifferentPayloadsDoNot() {
        UUID source = UUID.randomUUID();
        UUID destination = UUID.randomUUID();
        String first = RequestFingerprint.transfer(source, destination, Money.parse("USD", "10"));
        String second = RequestFingerprint.transfer(source, destination, Money.parse("USD", "10.00"));
        String differentAmount = RequestFingerprint.transfer(source, destination, Money.parse("USD", "10.01"));

        assertThat(first).isEqualTo(second).hasSize(64);
        assertThat(differentAmount).isNotEqualTo(first);
        assertThat(RequestFingerprint.deposit(source, Money.parse("USD", "10.00")))
                .isNotEqualTo(first);
    }
}
