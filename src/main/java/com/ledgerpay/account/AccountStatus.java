package com.ledgerpay.account;

import com.ledgerpay.error.ApiException;
import com.ledgerpay.error.ErrorCode;

public enum AccountStatus {
    ACTIVE,
    FROZEN,
    CLOSED;

    public boolean canMoveFunds() {
        return this == ACTIVE;
    }

    public AccountStatus transitionTo(AccountStatus next) {
        if (next == null) {
            throw new ApiException(ErrorCode.INVALID_STATUS_TRANSITION, "Status is required.");
        }
        if (this == next) {
            return this;
        }
        boolean allowed = switch (this) {
            case ACTIVE -> next == FROZEN || next == CLOSED;
            case FROZEN -> next == ACTIVE || next == CLOSED;
            case CLOSED -> false;
        };
        if (!allowed) {
            throw new ApiException(
                    ErrorCode.INVALID_STATUS_TRANSITION,
                    "Cannot change status from " + this + " to " + next + ".");
        }
        return next;
    }
}
