package com.ledgerpay.account;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    Page<Account> findByOwnerIdAndKind(UUID ownerId, AccountKind kind, Pageable pageable);

    Optional<Account> findByKindAndCurrency(AccountKind kind, String currency);

    boolean existsByIdAndOwnerId(UUID id, UUID ownerId);
}
