package com.ledgerpay.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ledgerpay.error.ApiException;
import com.ledgerpay.error.ErrorCode;
import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void parsesScaleAndRejectsExtraFractionDigits() {
        assertThat(Money.parse("USD", "10").minorUnits()).isEqualTo(1000);
        assertThat(Money.parse("USD", "10.5").minorUnits()).isEqualTo(1050);
        assertThat(Money.parse("USD", "10.50").minorUnits()).isEqualTo(1050);
        assertThat(Money.parse("USD", "0.01").formatted()).isEqualTo("0.01");
        assertThat(Money.parse("JPY", "500").minorUnits()).isEqualTo(500);
        assertThat(Money.parse("JPY", "500").formatted()).isEqualTo("500");

        assertThatThrownBy(() -> Money.parse("USD", "10.501"))
                .isInstanceOfSatisfying(ApiException.class, error ->
                        assertThat(error.errorCode()).isEqualTo(ErrorCode.INVALID_AMOUNT));
        assertThatThrownBy(() -> Money.parse("JPY", "500.0"))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> Money.parse("usd", "10"))
                .isInstanceOfSatisfying(ApiException.class, error ->
                        assertThat(error.errorCode()).isEqualTo(ErrorCode.UNSUPPORTED_CURRENCY));
        assertThatThrownBy(() -> Money.parse("USD", "0"))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> Money.parse("USD", "-1.00"))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> Money.parse("CHF", "10.00"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void formatsZeroBalanceWithoutFloatingPoint() {
        assertThat(LedgerCurrency.USD.format(0)).isEqualTo("0.00");
        assertThat(LedgerCurrency.JPY.format(0)).isEqualTo("0");
        assertThat(LedgerCurrency.EUR.format(1050)).isEqualTo("10.50");
    }
}
