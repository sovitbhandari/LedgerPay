package com.ledgerpay.transfer;

import com.ledgerpay.money.Money;
import java.util.UUID;

public record PostingCommand(
        UUID requesterId,
        String idempotencyKey,
        String requestHash,
        TransferKind kind,
        UUID sourceAccountId,
        UUID destinationAccountId,
        Money money) {
}
