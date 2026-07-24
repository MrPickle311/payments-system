package com.example.payments.wallet.infrastructure.persistence;

import com.example.payments.wallet.application.port.WalletAccountPort;
import com.example.payments.wallet.domain.WalletAccount;
import com.example.payments.wallet.infrastructure.persistence.mapper.WalletAccountEntityMapper;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WalletAccountRepositoryAdapter implements WalletAccountPort {

    private final WalletAccountRepository walletAccountRepository;
    private final WalletAccountEntityMapper mapper;

    @Override
    public Optional<WalletAccount> findByUserIdAndCurrency(Long userId, String currency) {
        return walletAccountRepository.findByUserIdAndCurrency(userId, currency).map(mapper::toDomain);
    }

    @Override
    public WalletAccount save(WalletAccount account) {
        WalletAccountEntity entity = mapper.toEntity(account);
        WalletAccountEntity savedEntity = walletAccountRepository.save(entity);
        return mapper.toDomain(savedEntity);
    }
}
