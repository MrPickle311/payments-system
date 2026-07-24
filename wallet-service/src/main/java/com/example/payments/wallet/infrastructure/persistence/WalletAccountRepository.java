package com.example.payments.wallet.infrastructure.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WalletAccountRepository extends JpaRepository<WalletAccountEntity, Long> {
    Optional<WalletAccountEntity> findByUserIdAndCurrency(Long userId, String currency);
}
