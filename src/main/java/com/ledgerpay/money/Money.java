package com.ledgerpay.money;

/**
 * A positive amount in one supported currency, stored as integer minor units.
 */
public record Money(LedgerCurrency currency, long minorUnits) {

    public Money {
        if (currency == null) {
            throw new IllegalArgumentException("currency is required");
        }
        if (minorUnits <= 0 || minorUnits > LedgerCurrency.MAX_MINOR_UNITS) {
            throw new IllegalArgumentException("minor units are outside the supported range");
        }
    }

    public static Money parse(String currencyCode, String amount) {
        LedgerCurrency currency = LedgerCurrency.require(currencyCode);
        return new Money(currency, currency.toMinorUnits(amount));
    }

    public String formatted() {
        return currency.format(minorUnits);
    }
}
