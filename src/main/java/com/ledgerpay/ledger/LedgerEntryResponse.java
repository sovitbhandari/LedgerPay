package com.ledgerpay.ledger;

import java.time.Instant;
import java.util.UUID;

public record LedgerEntryResponse(
        UUID id,
        UUID transferId,
        EntryDirection direction,
        String currency,
        int scale,
        long amountMinor,
        String amount,
        Instant createdAt) {
}
