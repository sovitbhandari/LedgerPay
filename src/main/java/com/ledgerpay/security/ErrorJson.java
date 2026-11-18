package com.ledgerpay.security;

import java.time.Clock;
import java.time.Instant;

final class ErrorJson {

    private ErrorJson() {
    }

    static String body(Clock clock, int status, String error, String code, String message, String path) {
        return "{\"timestamp\":\"" + Instant.now(clock) + "\""
                + ",\"status\":" + status
                + ",\"error\":\"" + escape(error) + "\""
                + ",\"code\":\"" + escape(code) + "\""
                + ",\"message\":\"" + escape(message) + "\""
                + ",\"path\":\"" + escape(path) + "\"}";
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
