package com.example.payments.wallet.application;

import static com.example.payments.wallet.common.WalletConstants.STATUS_INSUFFICIENT_FUNDS;
import static com.example.payments.wallet.common.WalletConstants.STATUS_SUCCESS;

import com.example.payments.wallet.application.port.WalletAccountPort;
import com.example.payments.wallet.domain.WalletAccount;
import com.example.payments.wallet.grpc.DebitRequest;
import com.example.payments.wallet.infrastructure.persistence.IdempotencyKeyEntity;
import com.example.payments.wallet.infrastructure.persistence.IdempotencyRepository;
import io.micrometer.observation.annotation.Observed;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class WalletService {

    public static final BigDecimal DEFAULT_MOCK_BALANCE = new BigDecimal("1000.00");
    private final WalletAccountPort walletAccountPort;
    private final IdempotencyRepository idempotencyRepository;

    @Transactional
    @Observed(name = "debit-between-users")
    public String debitBetweenUsers(DebitRequest request) {
        String key = request.getIdempotencyKey();
        if (!key.isBlank()) {
            var existing = idempotencyRepository.findById(key);
            if (existing.isPresent()) {
                log.warn("[WalletService] Duplicate debit, returning cached status for key={}", key);
                return existing.get().getStatus();
            }
        }

        BigDecimal amount = parseAmount(request.getAmount(), request.getPaymentId());
        if (amount == null) {
            return saveIdempotencyAndReturn(key, STATUS_INSUFFICIENT_FUNDS);
        }
        WalletAccount sourceWalletAccount = getOrCreateAccountForUpdate(request.getSourceUserId(), request.getCurrency());
        if (sourceWalletAccount.getBalance().compareTo(amount) < 0) {
            return saveIdempotencyAndReturn(key, STATUS_INSUFFICIENT_FUNDS);
        }
        transferMoney(sourceWalletAccount, request.getTargetUserId(), amount, request.getCurrency());
        return saveIdempotencyAndReturn(key, STATUS_SUCCESS);
    }

    private String saveIdempotencyAndReturn(String key, String status) {
        if (!key.isBlank()) {
            idempotencyRepository.save(IdempotencyKeyEntity.builder()
                    .idempotencyKey(key)
                    .status(status)
                    .build());
        }
        return status;
    }

    private BigDecimal parseAmount(String amount, Long paymentId) {
        try {
            return new BigDecimal(amount);
        } catch (NumberFormatException e) {
            log.error("[WalletService] Invalid amount '{}' for paymentId={}", amount, paymentId, e);
            return null;
        }
    }

    private void transferMoney(WalletAccount source, long targetId, BigDecimal amount, String currency) {
        source.setBalance(source.getBalance().subtract(amount));
        walletAccountPort.save(source);
        WalletAccount target = getOrCreateAccountForUpdate(targetId, currency);
        target.setBalance(target.getBalance().add(amount));
        walletAccountPort.save(target);
    }

    private WalletAccount getOrCreateAccountForUpdate(long userId, String currency) {
        return walletAccountPort
                .findByUserIdAndCurrencyForUpdate(userId, currency)
                .orElseGet(() -> createMockAccount(userId, currency));
    }

    private WalletAccount createMockAccount(long userId, String currency) {
        WalletAccount newAccount = WalletAccount.builder()
                .id(userId)
                .userId(userId)
                .balance(DEFAULT_MOCK_BALANCE)
                .currency(currency)
                .build();
        return walletAccountPort.save(newAccount);
    }
}
