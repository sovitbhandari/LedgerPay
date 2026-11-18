package com.ledgerpay.account;

import java.util.List;

public record AccountPageResponse(
        int page, int size, long totalElements, int totalPages, List<AccountResponse> accounts) {
}
