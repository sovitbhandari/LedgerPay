package com.ledgerpay.transfer;

import com.ledgerpay.account.AccountRepository;
import com.ledgerpay.error.ApiException;
import com.ledgerpay.error.ErrorCode;
import com.ledgerpay.idempotency.IdempotencyKeys;
import com.ledgerpay.idempotency.IdempotencyService;
import com.ledgerpay.idempotency.RequestFingerprint;
import com.ledgerpay.ledger.LedgerEntryRepository;
import com.ledgerpay.money.Money;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransferService {

    private final IdempotencyService idempotency;
    private final AccountRepository accounts;
    private final TransferRepository transfers;
    private final LedgerEntryRepository entries;

    public TransferService(
            IdempotencyService idempotency,
            AccountRepository accounts,
            TransferRepository transfers,
            LedgerEntryRepository entries) {
        this.idempotency = idempotency;
        this.accounts = accounts;
        this.transfers = transfers;
        this.entries = entries;
    }

    public TransferResponse create(UUID userId, String idempotencyKey, CreateTransferRequest request) {
        String key = IdempotencyKeys.require(idempotencyKey);
        Money money = Money.parse(request.currency(), request.amount());
        String hash = RequestFingerprint.transfer(request.sourceAccountId(), request.destinationAccountId(), money);
        return idempotency.execute(new PostingCommand(
                userId,
                key,
                hash,
                TransferKind.TRANSFER,
                request.sourceAccountId(),
                request.destinationAccountId(),
                money));
    }

    public TransferResponse deposit(UUID userId, UUID accountId, String idempotencyKey, String amount, String currency) {
        String key = IdempotencyKeys.require(idempotencyKey);
        Money money = Money.parse(currency, amount);
        String hash = RequestFingerprint.deposit(accountId, money);
        return idempotency.execute(new PostingCommand(
                userId, key, hash, TransferKind.DEPOSIT, null, accountId, money));
    }

    @Transactional(readOnly = true)
    public TransferResponse get(UUID userId, UUID transferId) {
        Transfer transfer = transfers.findById(transferId)
                .orElseThrow(() -> new ApiException(ErrorCode.TRANSFER_NOT_FOUND, "Transfer was not found."));
        boolean visible = transfer.getRequesterId().equals(userId)
                || accounts.existsByIdAndOwnerId(transfer.getSourceAccountId(), userId)
                || accounts.existsByIdAndOwnerId(transfer.getDestinationAccountId(), userId);
        if (!visible) {
            throw new ApiException(ErrorCode.TRANSFER_NOT_FOUND, "Transfer was not found.");
        }
        return TransferMapper.toResponse(transfer, entries.findByTransferIdOrderByDirectionAsc(transferId));
    }
}
