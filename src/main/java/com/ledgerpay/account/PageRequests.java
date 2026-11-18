package com.ledgerpay.account;

import com.ledgerpay.error.ApiException;
import com.ledgerpay.error.ErrorCode;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public final class PageRequests {

    private PageRequests() {
    }

    public static Pageable of(int page, int size) {
        if (page < 0) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "page must be zero or greater.");
        }
        if (size < 1 || size > 100) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "size must be between 1 and 100.");
        }
        return PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
    }
}
