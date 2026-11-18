package com.ledgerpay.account;

import com.ledgerpay.ledger.LedgerPageResponse;
import com.ledgerpay.security.CurrentUser;
import com.ledgerpay.transfer.TransferResponse;
import com.ledgerpay.transfer.TransferService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private final AccountService accounts;
    private final TransferService transfers;

    public AccountController(AccountService accounts, TransferService transfers) {
        this.accounts = accounts;
        this.transfers = transfers;
    }

    @GetMapping
    public AccountPageResponse list(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return accounts.list(CurrentUser.id(authentication), page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse create(Authentication authentication, @Valid @RequestBody CreateAccountRequest request) {
        return accounts.create(CurrentUser.id(authentication), request.name(), request.currency());
    }

    @GetMapping("/{id}/balance")
    public BalanceResponse balance(Authentication authentication, @PathVariable UUID id) {
        return accounts.balance(CurrentUser.id(authentication), id);
    }

    @GetMapping("/{id}/ledger")
    public LedgerPageResponse ledger(
            Authentication authentication,
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return accounts.ledger(CurrentUser.id(authentication), id, page, size);
    }

    @PostMapping("/{id}/status")
    public AccountResponse changeStatus(
            Authentication authentication, @PathVariable UUID id, @Valid @RequestBody ChangeStatusRequest request) {
        return accounts.changeStatus(CurrentUser.id(authentication), id, request.status());
    }

    @PostMapping("/{id}/deposits")
    @ResponseStatus(HttpStatus.CREATED)
    public TransferResponse deposit(
            Authentication authentication,
            @PathVariable UUID id,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateDepositRequest request) {
        var account = accounts.ownedCustomerAccount(CurrentUser.id(authentication), id);
        return transfers.deposit(
                CurrentUser.id(authentication), id, idempotencyKey, request.amount(), account.getCurrency());
    }
}
