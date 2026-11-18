package com.ledgerpay.account;

import java.util.UUID;

public record BalanceResponse(UUID accountId, String currency, int scale, long balanceMinor, String balance) {
}
