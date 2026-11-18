package com.ledgerpay.account;

import com.ledgerpay.ledger.LedgerEntry;
import com.ledgerpay.ledger.LedgerEntryResponse;
import com.ledgerpay.ledger.LedgerPageResponse;
import com.ledgerpay.money.LedgerCurrency;
import org.springframework.data.domain.Page;

public final class AccountMapper {

    private AccountMapper() {
    }

    public static AccountResponse toResponse(Account account) {
        LedgerCurrency currency = LedgerCurrency.require(account.getCurrency());
        return new AccountResponse(
                account.getId(),
                account.getName(),
                currency.name(),
                currency.scale(),
                account.getStatus().name(),
                account.getBalanceMinor(),
                currency.format(account.getBalanceMinor()),
                account.getCreatedAt());
    }

    public static BalanceResponse toBalance(Account account) {
        LedgerCurrency currency = LedgerCurrency.require(account.getCurrency());
        return new BalanceResponse(
                account.getId(),
                currency.name(),
                currency.scale(),
                account.getBalanceMinor(),
                currency.format(account.getBalanceMinor()));
    }

    public static LedgerPageResponse toLedgerPage(Account account, Page<LedgerEntry> page) {
        LedgerCurrency currency = LedgerCurrency.require(account.getCurrency());
        return new LedgerPageResponse(
                account.getId(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.getContent().stream().map(AccountMapper::toEntry).toList());
    }

    private static LedgerEntryResponse toEntry(LedgerEntry entry) {
        LedgerCurrency currency = LedgerCurrency.require(entry.getCurrency());
        return new LedgerEntryResponse(
                entry.getId(),
                entry.getTransferId(),
                entry.getDirection(),
                currency.name(),
                currency.scale(),
                entry.getAmountMinor(),
                currency.format(entry.getAmountMinor()),
                entry.getCreatedAt());
    }
}
