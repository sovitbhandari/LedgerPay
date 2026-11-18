package com.ledgerpay.transfer;

import com.ledgerpay.ledger.LedgerEntry;
import com.ledgerpay.money.LedgerCurrency;
import java.util.Comparator;
import java.util.List;

public final class TransferMapper {

    private TransferMapper() {
    }

    public static TransferResponse toResponse(Transfer transfer, List<LedgerEntry> entries) {
        LedgerCurrency currency = LedgerCurrency.require(transfer.getCurrency());
        List<TransferResponse.EntryLine> lines = entries.stream()
                .sorted(Comparator.comparing(LedgerEntry::getDirection).thenComparing(LedgerEntry::getId))
                .map(entry -> new TransferResponse.EntryLine(
                        entry.getId(),
                        entry.getAccountId(),
                        entry.getDirection(),
                        entry.getAmountMinor(),
                        entry.getCurrency()))
                .toList();
        return new TransferResponse(
                transfer.getId(),
                transfer.getKind().name(),
                transfer.getStatus().name(),
                transfer.getSourceAccountId(),
                transfer.getDestinationAccountId(),
                currency.name(),
                currency.scale(),
                transfer.getAmountMinor(),
                currency.format(transfer.getAmountMinor()),
                lines,
                transfer.getCreatedAt());
    }
}
