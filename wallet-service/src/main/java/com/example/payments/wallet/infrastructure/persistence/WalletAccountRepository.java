package com.example.payments.wallet.infrastructure.persistence;

import com.example.payments.wallet.domain.WalletAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WalletAccountRepository extends JpaRepository<WalletAccount, Long> {
  Optional<WalletAccount> findByUserIdAndCurrency(Long userId, String currency);
}
