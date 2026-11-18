package com.ledgerpay.transfer;

import com.ledgerpay.ledger.LedgerEntry;
import java.util.List;

public record PostedTransfer(Transfer transfer, List<LedgerEntry> entries) {
}
