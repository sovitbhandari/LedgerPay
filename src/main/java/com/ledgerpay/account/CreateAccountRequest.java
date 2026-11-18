package com.ledgerpay.account;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateAccountRequest(
        @NotBlank @Size(max = 80) String name,
        @NotBlank @Size(min = 3, max = 3) String currency) {
}
