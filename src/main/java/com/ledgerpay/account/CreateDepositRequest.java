package com.ledgerpay.account;

import jakarta.validation.constraints.NotBlank;

public record CreateDepositRequest(@NotBlank String amount) {
}
