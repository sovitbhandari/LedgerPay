package com.ledgerpay.security;

import com.ledgerpay.error.ApiException;
import com.ledgerpay.error.ErrorCode;
import java.util.UUID;
import org.springframework.security.core.Authentication;

public final class CurrentUser {

    private CurrentUser() {
    }

    public static UUID id(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof JwtPrincipal principal)) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED, "Authentication is required.");
        }
        return principal.userId();
    }
}
