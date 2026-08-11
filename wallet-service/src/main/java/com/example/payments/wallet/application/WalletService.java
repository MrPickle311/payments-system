package com.example.payments.wallet.application;

import static com.example.payments.wallet.common.WalletConstants.STATUS_IN_PROGRESS;
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
            int claimed = idempotencyRepository.tryInsert(key, STATUS_IN_PROGRESS);
            if (claimed == 0) {
                log.warn("[WalletService] Duplicate debit, returning cached status for key={}", key);
                return idempotencyRepository
                        .findById(key)
                        .map(IdempotencyKeyEntity::getStatus)
                        .orElse(STATUS_IN_PROGRESS);
            }
        }

        BigDecimal amount = parseAmount(request.getAmount(), request.getPaymentId());
        if (amount == null) {
            return saveIdempotencyAndReturn(key, STATUS_INSUFFICIENT_FUNDS);
        }
        log.info("Attempting to getOrCreateAccountForUpdate: {}", request.getPaymentId());
        WalletAccount sourceWalletAccount =
                getOrCreateAccountForUpdate(request.getSourceUserId(), request.getCurrency());
        if (sourceWalletAccount.getBalance().compareTo(amount) < 0) {
            return saveIdempotencyAndReturn(key, STATUS_INSUFFICIENT_FUNDS);
        }
        log.info("Attempting to transferMoney: {}", request.getPaymentId());
        transferMoney(sourceWalletAccount, request.getTargetUserId(), amount, request.getCurrency());
        return saveIdempotencyAndReturn(key, STATUS_SUCCESS);
    }

    private String saveIdempotencyAndReturn(String key, String status) {
        if (!key.isBlank()) {
            log.info("Attempting to save idempotency key for key: {}, status: {}", key, status);
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
        WalletAccount target = getOrCreateAccountForUpdate(targetId, currency);
        source.setBalance(source.getBalance().subtract(amount));
        target.setBalance(target.getBalance().add(amount));
        walletAccountPort.save(source);
        walletAccountPort.save(target);
    }

    private WalletAccount getOrCreateAccountForUpdate(long userId, String currency) {
        return walletAccountPort
                .findByUserIdAndCurrencyForUpdate(userId, currency)
                .orElseGet(() -> createMockAccount(userId, currency));
    }

    private WalletAccount createMockAccount(long userId, String currency) {
        long uniqueAccountId = Math.abs(java.util.Objects.hash(userId, currency));
        if (uniqueAccountId == userId) {
            uniqueAccountId = uniqueAccountId + 1000000L;
        }
        WalletAccount newAccount = WalletAccount.builder()
                .id(uniqueAccountId)
                .userId(userId)
                .balance(DEFAULT_MOCK_BALANCE)
                .currency(currency)
                .build();
        return walletAccountPort.save(newAccount);
    }
}
