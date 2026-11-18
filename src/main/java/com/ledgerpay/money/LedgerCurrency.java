package com.ledgerpay.money;

import com.ledgerpay.error.ApiException;
import com.ledgerpay.error.ErrorCode;
import java.util.Arrays;
import java.util.Locale;

/**
 * Explicit currency scale. Amounts are never stored as floating-point values.
 * USD 10.50 is 1050 minor units. JPY 500 is 500 minor units.
 */
public enum LedgerCurrency {
    USD(2),
    EUR(2),
    GBP(2),
    JPY(0);

    public static final long MAX_MINOR_UNITS = 1_000_000_000_000L;

    private final int scale;

    LedgerCurrency(int scale) {
        this.scale = scale;
    }

    public int scale() {
        return scale;
    }

    public static LedgerCurrency require(String code) {
        if (code == null || code.isBlank()) {
            throw new ApiException(ErrorCode.UNSUPPORTED_CURRENCY, "Currency is required.");
        }
        String normalized = code.trim();
        if (!normalized.equals(normalized.toUpperCase(Locale.ROOT))) {
            throw new ApiException(ErrorCode.UNSUPPORTED_CURRENCY, "Currency must be an uppercase ISO code.");
        }
        return Arrays.stream(values())
                .filter(currency -> currency.name().equals(normalized))
                .findFirst()
                .orElseThrow(() -> new ApiException(
                        ErrorCode.UNSUPPORTED_CURRENCY,
                        "Currency " + normalized + " is not supported. Supported currencies: USD, EUR, GBP, JPY."));
    }

    public long toMinorUnits(String rawAmount) {
        if (rawAmount == null) {
            throw invalid("Amount is required.");
        }
        String amount = rawAmount.trim();
        if (!amount.matches("\\d{1,15}(?:\\.\\d{1,8})?")) {
            throw invalid("Amount must be a positive decimal such as 10.50.");
        }
        String whole = amount;
        String fraction = "";
        int dot = amount.indexOf('.');
        if (dot >= 0) {
            whole = amount.substring(0, dot);
            fraction = amount.substring(dot + 1);
        }
        if (fraction.length() > scale) {
            throw invalid("Amount has more fractional digits than " + name() + " supports (" + scale + ").");
        }
        StringBuilder padded = new StringBuilder(fraction);
        while (padded.length() < scale) {
            padded.append('0');
        }
        String digits = stripLeadingZeros(whole) + padded;
        if (digits.isEmpty()) {
            digits = "0";
        }
        long minor;
        try {
            minor = Long.parseLong(digits);
        } catch (NumberFormatException exception) {
            throw invalid("Amount is outside the supported range.");
        }
        if (minor <= 0) {
            throw invalid("Amount must be greater than zero.");
        }
        if (minor > MAX_MINOR_UNITS) {
            throw invalid("Amount exceeds the maximum of " + MAX_MINOR_UNITS + " minor units.");
        }
        return minor;
    }

    public String format(long minorUnits) {
        if (minorUnits < 0) {
            throw new IllegalArgumentException("Customer amounts are not negative");
        }
        if (scale == 0) {
            return Long.toString(minorUnits);
        }
        long factor = factor();
        long whole = minorUnits / factor;
        long fraction = minorUnits % factor;
        return whole + "." + leftPad(fraction, scale);
    }

    private long factor() {
        long factor = 1;
        for (int i = 0; i < scale; i++) {
            factor *= 10;
        }
        return factor;
    }

    private static String stripLeadingZeros(String whole) {
        int index = 0;
        while (index < whole.length() - 1 && whole.charAt(index) == '0') {
            index++;
        }
        return whole.substring(index);
    }

    private static String leftPad(long value, int width) {
        String text = Long.toString(value);
        StringBuilder builder = new StringBuilder();
        for (int i = text.length(); i < width; i++) {
            builder.append('0');
        }
        return builder.append(text).toString();
    }

    private static ApiException invalid(String message) {
        return new ApiException(ErrorCode.INVALID_AMOUNT, message);
    }
}
