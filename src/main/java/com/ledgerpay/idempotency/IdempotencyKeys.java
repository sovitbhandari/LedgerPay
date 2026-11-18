package com.ledgerpay.idempotency;

import com.ledgerpay.error.ApiException;
import com.ledgerpay.error.ErrorCode;
import java.util.regex.Pattern;

public final class IdempotencyKeys {

    private static final Pattern KEY = Pattern.compile("^[A-Za-z0-9._~-]{8,200}$");

    private IdempotencyKeys() {
    }

    public static String require(String key) {
        if (key == null || key.isBlank()) {
            throw new ApiException(ErrorCode.IDEMPOTENCY_KEY_REQUIRED, "Idempotency-Key header is required.");
        }
        String trimmed = key.trim();
        if (!KEY.matcher(trimmed).matches()) {
            throw new ApiException(
                    ErrorCode.IDEMPOTENCY_KEY_INVALID,
                    "Idempotency-Key must be 8 to 200 characters and use letters, digits, or . _ ~ -.");
        }
        return trimmed;
    }
}
