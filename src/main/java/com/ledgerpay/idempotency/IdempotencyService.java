package com.ledgerpay.idempotency;

import com.ledgerpay.error.ApiException;
import com.ledgerpay.error.ErrorCode;
import com.ledgerpay.ledger.LedgerEntryRepository;
import com.ledgerpay.transfer.LedgerPostingService;
import com.ledgerpay.transfer.PostingCommand;
import com.ledgerpay.transfer.PostedTransfer;
import com.ledgerpay.transfer.Transfer;
import com.ledgerpay.transfer.TransferMapper;
import com.ledgerpay.transfer.TransferRepository;
import com.ledgerpay.transfer.TransferResponse;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class IdempotencyService {

    private final IdempotencyRecordRepository records;
    private final TransferRepository transfers;
    private final LedgerEntryRepository entries;
    private final LedgerPostingService posting;

    public IdempotencyService(
            IdempotencyRecordRepository records,
            TransferRepository transfers,
            LedgerEntryRepository entries,
            LedgerPostingService posting) {
        this.records = records;
        this.transfers = transfers;
        this.entries = entries;
        this.posting = posting;
    }

    public TransferResponse execute(PostingCommand command) {
        Optional<IdempotencyRecord> existing = records.findByRequesterIdAndIdempotencyKey(
                command.requesterId(), command.idempotencyKey());
        if (existing.isPresent()) {
            return replay(existing.get(), command.requestHash());
        }
        try {
            PostedTransfer posted = posting.post(command);
            return TransferMapper.toResponse(posted.transfer(), posted.entries());
        } catch (ApiException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            if (!isIdempotencyConflict(exception)) {
                throw exception;
            }
            IdempotencyRecord winner = records.findByRequesterIdAndIdempotencyKey(
                            command.requesterId(), command.idempotencyKey())
                    .orElseThrow(() -> exception);
            return replay(winner, command.requestHash());
        }
    }

    private TransferResponse replay(IdempotencyRecord record, String requestHash) {
        if (!record.getRequestHash().equals(requestHash)) {
            throw new ApiException(
                    ErrorCode.IDEMPOTENCY_KEY_REUSED,
                    "This Idempotency-Key was already used with a different request.");
        }
        if (record.getTransferId() == null) {
            throw new ApiException(ErrorCode.DATA_CONFLICT, "The original request is still being recorded.");
        }
        Transfer transfer = transfers.findById(record.getTransferId())
                .orElseThrow(() -> new ApiException(ErrorCode.TRANSFER_NOT_FOUND, "The original transfer is missing."));
        return TransferMapper.toResponse(
                transfer, entries.findByTransferIdOrderByDirectionAsc(transfer.getId()));
    }

    private static boolean isIdempotencyConflict(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof SQLException sql && "23505".equals(sql.getSQLState())) {
                return true;
            }
            String message = current.getMessage();
            if (message != null && (message.contains("uq_idempotency_requester_key")
                    || message.contains("duplicate key value"))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
