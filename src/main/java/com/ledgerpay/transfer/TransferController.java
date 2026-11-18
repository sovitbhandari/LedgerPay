package com.ledgerpay.transfer;

import com.ledgerpay.security.CurrentUser;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transfers")
public class TransferController {

    private final TransferService transfers;

    public TransferController(TransferService transfers) {
        this.transfers = transfers;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransferResponse create(
            Authentication authentication,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateTransferRequest request) {
        return transfers.create(CurrentUser.id(authentication), idempotencyKey, request);
    }

    @GetMapping("/{id}")
    public TransferResponse get(Authentication authentication, @PathVariable UUID id) {
        return transfers.get(CurrentUser.id(authentication), id);
    }
}
