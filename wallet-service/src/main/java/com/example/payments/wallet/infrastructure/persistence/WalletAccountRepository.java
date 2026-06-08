package com.example.payments.wallet.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WalletAccountRepository extends JpaRepository<WalletAccountEntity, Long> {
  Optional<WalletAccountEntity> findByUserIdAndCurrency(Long userId, String currency);
}
