package com.ledgerpay.account;

import jakarta.validation.constraints.NotNull;

public record ChangeStatusRequest(@NotNull AccountStatus status) {
}
