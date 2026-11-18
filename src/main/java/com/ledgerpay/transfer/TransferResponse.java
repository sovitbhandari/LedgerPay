package com.ledgerpay.transfer;

import com.ledgerpay.ledger.EntryDirection;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TransferResponse(
        UUID id,
        String kind,
        String status,
        UUID sourceAccountId,
        UUID destinationAccountId,
        String currency,
        int scale,
        long amountMinor,
        String amount,
        List<EntryLine> entries,
        Instant createdAt) {

    public record EntryLine(UUID id, UUID accountId, EntryDirection direction, long amountMinor, String currency) {
    }
}
