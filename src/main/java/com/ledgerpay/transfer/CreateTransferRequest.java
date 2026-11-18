package com.ledgerpay.transfer;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateTransferRequest(
        @NotNull UUID sourceAccountId,
        @NotNull UUID destinationAccountId,
        @NotBlank String currency,
        @NotBlank String amount) {
}
