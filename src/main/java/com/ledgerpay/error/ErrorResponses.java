package com.ledgerpay.error;

import java.time.Clock;
import java.time.Instant;

public final class ErrorResponses {

    private ErrorResponses() {
    }

    public static ErrorResponse of(Clock clock, ErrorCode code, String message, String path) {
        return of(clock, code, message, path, null);
    }

    public static ErrorResponse of(
            Clock clock, ErrorCode code, String message, String path, java.util.List<FieldErrorItem> fieldErrors) {
        return new ErrorResponse(
                Instant.now(clock),
                code.status().value(),
                code.status().getReasonPhrase(),
                code.name(),
                message,
                path,
                fieldErrors);
    }
}
