package com.ledgerpay.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ledgerpay.error.ApiException;
import com.ledgerpay.error.ErrorCode;
import com.ledgerpay.ledger.EntryDirection;
import com.ledgerpay.ledger.LedgerEntry;
import com.ledgerpay.ledger.LedgerEntryRepository;
import com.ledgerpay.money.Money;
import com.ledgerpay.transfer.LedgerPostingService;
import com.ledgerpay.transfer.PostedTransfer;
import com.ledgerpay.transfer.PostingCommand;
import com.ledgerpay.transfer.Transfer;
import com.ledgerpay.transfer.TransferKind;
import com.ledgerpay.transfer.TransferRepository;
import com.ledgerpay.transfer.TransferResponse;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    @Mock
    private IdempotencyRecordRepository records;

    @Mock
    private TransferRepository transfers;

    @Mock
    private LedgerEntryRepository entries;

    @Mock
    private LedgerPostingService posting;

    @Test
    void sameKeyAndPayloadDoesNotPostAgain() {
        IdempotencyService service = service();
        UUID userId = UUID.randomUUID();
        UUID source = UUID.randomUUID();
        UUID destination = UUID.randomUUID();
        Money money = Money.parse("USD", "5.00");
        PostingCommand command = command(userId, source, destination, money);
        Transfer transfer = sampleTransfer(userId, source, destination, money);
        IdempotencyRecord record = IdempotencyRecord.claim(userId, command.idempotencyKey(), command.requestHash(), Instant.parse("2026-01-01T00:00:00Z"));
        record.assignTransfer(transfer.getId());
        when(records.findByRequesterIdAndIdempotencyKey(userId, command.idempotencyKey())).thenReturn(Optional.of(record));
        when(transfers.findById(transfer.getId())).thenReturn(Optional.of(transfer));
        when(entries.findByTransferIdOrderByDirectionAsc(transfer.getId())).thenReturn(List.of(
                LedgerEntry.of(transfer.getId(), source, EntryDirection.DEBIT, money.minorUnits(), "USD", transfer.getCreatedAt()),
                LedgerEntry.of(transfer.getId(), destination, EntryDirection.CREDIT, money.minorUnits(), "USD", transfer.getCreatedAt())));

        TransferResponse response = service.execute(command);

        assertThat(response.id()).isEqualTo(transfer.getId());
        assertThat(response.entries()).hasSize(2);
        verify(posting, never()).post(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void reusedKeyWithADifferentPayloadConflicts() {
        IdempotencyService service = service();
        UUID userId = UUID.randomUUID();
        Money money = Money.parse("USD", "5.00");
        PostingCommand command = command(userId, UUID.randomUUID(), UUID.randomUUID(), money);
        IdempotencyRecord record = IdempotencyRecord.claim(
                userId, command.idempotencyKey(), "a".repeat(64), Instant.parse("2026-01-01T00:00:00Z"));
        record.assignTransfer(UUID.randomUUID());
        when(records.findByRequesterIdAndIdempotencyKey(userId, command.idempotencyKey())).thenReturn(Optional.of(record));

        assertThatThrownBy(() -> service.execute(command))
                .isInstanceOfSatisfying(ApiException.class, error ->
                        assertThat(error.errorCode()).isEqualTo(ErrorCode.IDEMPOTENCY_KEY_REUSED));
        verify(posting, never()).post(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void duplicateInsertReplaysTheCommittedTransfer() {
        IdempotencyService service = service();
        UUID userId = UUID.randomUUID();
        UUID source = UUID.randomUUID();
        UUID destination = UUID.randomUUID();
        Money money = Money.parse("USD", "5.00");
        PostingCommand command = command(userId, source, destination, money);
        Transfer transfer = sampleTransfer(userId, source, destination, money);
        IdempotencyRecord record = IdempotencyRecord.claim(userId, command.idempotencyKey(), command.requestHash(), Instant.parse("2026-01-01T00:00:00Z"));
        record.assignTransfer(transfer.getId());
        when(records.findByRequesterIdAndIdempotencyKey(userId, command.idempotencyKey()))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(record));
        when(posting.post(command)).thenThrow(new DataIntegrityViolationException(
                "duplicate key value violates unique constraint \"uq_idempotency_requester_key\""));
        when(transfers.findById(transfer.getId())).thenReturn(Optional.of(transfer));
        when(entries.findByTransferIdOrderByDirectionAsc(transfer.getId())).thenReturn(List.of(
                LedgerEntry.of(transfer.getId(), source, EntryDirection.DEBIT, money.minorUnits(), "USD", transfer.getCreatedAt()),
                LedgerEntry.of(transfer.getId(), destination, EntryDirection.CREDIT, money.minorUnits(), "USD", transfer.getCreatedAt())));

        TransferResponse response = service.execute(command);

        assertThat(response.id()).isEqualTo(transfer.getId());
        assertThat(response.amountMinor()).isEqualTo(500);
    }

    private IdempotencyService service() {
        return new IdempotencyService(records, transfers, entries, posting);
    }

    private static PostingCommand command(UUID userId, UUID source, UUID destination, Money money) {
        return new PostingCommand(
                userId,
                "idem-key-1",
                RequestFingerprint.transfer(source, destination, money),
                TransferKind.TRANSFER,
                source,
                destination,
                money);
    }

    private static Transfer sampleTransfer(UUID userId, UUID source, UUID destination, Money money) {
        return Transfer.posted(
                userId,
                TransferKind.TRANSFER,
                source,
                destination,
                money.currency().name(),
                money.minorUnits(),
                Instant.parse("2026-01-01T00:00:00Z"));
    }
}
