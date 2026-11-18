package com.ledgerpay.ledger;

import java.util.List;
import java.util.UUID;

public record LedgerPageResponse(
        UUID accountId,
        int page,
        int size,
        long totalElements,
        int totalPages,
        List<LedgerEntryResponse> entries) {
}
