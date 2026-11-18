package com.ledgerpay.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ledgerpay.error.ApiException;
import com.ledgerpay.error.ErrorCode;
import org.junit.jupiter.api.Test;

class AccountStatusTest {

    @Test
    void allowsFreezeAndCloseAndRejectsReopen() {
        assertThat(AccountStatus.ACTIVE.transitionTo(AccountStatus.FROZEN)).isEqualTo(AccountStatus.FROZEN);
        assertThat(AccountStatus.FROZEN.transitionTo(AccountStatus.ACTIVE)).isEqualTo(AccountStatus.ACTIVE);
        assertThat(AccountStatus.FROZEN.transitionTo(AccountStatus.CLOSED)).isEqualTo(AccountStatus.CLOSED);
        assertThat(AccountStatus.ACTIVE.canMoveFunds()).isTrue();
        assertThat(AccountStatus.FROZEN.canMoveFunds()).isFalse();

        assertThatThrownBy(() -> AccountStatus.CLOSED.transitionTo(AccountStatus.ACTIVE))
                .isInstanceOfSatisfying(ApiException.class, error ->
                        assertThat(error.errorCode()).isEqualTo(ErrorCode.INVALID_STATUS_TRANSITION));
    }
}
