package com.ledgerpay.system;

import com.ledgerpay.account.Account;
import com.ledgerpay.account.AccountKind;
import com.ledgerpay.account.AccountRepository;
import com.ledgerpay.money.LedgerCurrency;
import com.ledgerpay.user.AppUser;
import com.ledgerpay.user.AppUserRepository;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Creates the internal funding accounts used by synthetic deposits.
 * The system user's password is a random value that is hashed and then discarded.
 */
@Component
public class SystemLedgerInitializer implements ApplicationRunner {

    static final String SYSTEM_EMAIL = "system@ledgerpay.internal";

    private final AppUserRepository users;
    private final AccountRepository accounts;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final TransactionTemplate transactions;

    public SystemLedgerInitializer(
            AppUserRepository users,
            AccountRepository accounts,
            PasswordEncoder passwordEncoder,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.users = users;
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Override
    public void run(ApplicationArguments args) {
        ensureSystemUser();
        for (LedgerCurrency currency : LedgerCurrency.values()) {
            try {
                transactions.executeWithoutResult(status -> ensureFundingAccount(currency));
            } catch (DataIntegrityViolationException exception) {
                // A concurrent startup inserted the same funding account.
            }
        }
    }

    private void ensureSystemUser() {
        if (users.findByEmail(SYSTEM_EMAIL).isPresent()) {
            return;
        }
        byte[] random = new byte[32];
        new SecureRandom().nextBytes(random);
        String discardedPassword = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        try {
            transactions.executeWithoutResult(status -> {
                if (users.findByEmail(SYSTEM_EMAIL).isEmpty()) {
                    users.save(AppUser.system(
                            SYSTEM_EMAIL, passwordEncoder.encode(discardedPassword), clock.instant()));
                }
            });
        } catch (DataIntegrityViolationException exception) {
            // A concurrent startup inserted the system user.
        }
    }

    private void ensureFundingAccount(LedgerCurrency currency) {
        AppUser system = users.findByEmail(SYSTEM_EMAIL)
                .orElseThrow(() -> new IllegalStateException("System user is missing"));
        if (accounts.findByKindAndCurrency(AccountKind.SYSTEM_FUNDING, currency.name()).isEmpty()) {
            accounts.save(Account.funding(system.getId(), currency, clock.instant()));
        }
    }
}
