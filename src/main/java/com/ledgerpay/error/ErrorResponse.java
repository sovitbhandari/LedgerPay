package com.ledgerpay.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String code,
        String message,
        String path,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) List<FieldErrorItem> fieldErrors) {
}
