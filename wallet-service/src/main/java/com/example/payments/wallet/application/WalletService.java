package com.example.payments.wallet.application;

import com.example.payments.common.dto.DebitRequest;
import com.example.payments.common.dto.DebitResponse;
import com.example.payments.wallet.domain.WalletAccount;
import com.example.payments.wallet.infrastructure.persistence.WalletAccountRepository;
import io.micrometer.observation.annotation.Observed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WalletService {
    private final WalletAccountRepository walletAccountRepository;

    @Transactional
    @Observed(name = "debit-wallet")
    public DebitResponse debit(DebitRequest request) {
        log.info("[WalletService] Processing debit for paymentId={} amount={} {}",
                request.getPaymentId(), request.getAmount(), request.getCurrency());

        // In a real system, we'd use userId from request or context
        // Here we'll just use a mock userId or find by paymentId if we had that mapping
        // For simplicity, let's assume we find by currency for a "global" test account
        WalletAccount account = walletAccountRepository.findByUserIdAndCurrency(1L, request.getCurrency())
                .orElseGet(() -> walletAccountRepository.save(WalletAccount.builder()
                        .id(1L)
                        .userId(1L)
                        .balance(new java.math.BigDecimal("1000.00"))
                        .currency(request.getCurrency())
                        .build()));

        if (account.getBalance().compareTo(request.getAmount()) < 0) {
            log.warn("[WalletService] Insufficient funds for account userId={} balance={} requested={}",
                    account.getUserId(), account.getBalance(), request.getAmount());
            return DebitResponse.builder()
                    .status("INSUFFICIENT_FUNDS")
                    .build();
        }

        account.setBalance(account.getBalance().subtract(request.getAmount()));
        walletAccountRepository.save(account);

        log.info("[WalletService] Debit successful for paymentId={} new balance={}",
                request.getPaymentId(), account.getBalance());

        return DebitResponse.builder()
                .status("SUCCESS")
                .referenceId("WLT-" + UUID.randomUUID())
                .build();
    }
}
