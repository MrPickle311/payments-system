package com.example.payments.wallet.application.port;

import com.example.payments.wallet.domain.WalletAccount;
import java.util.Optional;

public interface WalletAccountPort {
    Optional<WalletAccount> findByUserIdAndCurrency(Long userId, String currency);

    WalletAccount save(WalletAccount account);
}
