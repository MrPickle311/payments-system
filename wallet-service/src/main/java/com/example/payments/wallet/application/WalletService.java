package com.example.payments.wallet.application;

import static com.example.payments.wallet.common.WalletConstants.STATUS_INSUFFICIENT_FUNDS;
import static com.example.payments.wallet.common.WalletConstants.STATUS_SUCCESS;

import com.example.payments.wallet.application.port.WalletAccountPort;
import com.example.payments.wallet.domain.WalletAccount;
import com.example.payments.wallet.grpc.DebitRequest;
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

    @Transactional
    @Observed(name = "debit-between-users")
    public String debitBetweenUsers(DebitRequest request) {
        BigDecimal amount = parseAmount(request.getAmount(), request.getPaymentId());
        if (amount == null) {
            return STATUS_INSUFFICIENT_FUNDS;
        }
        WalletAccount sourceWalletAccount = getOrCreateAccount(request.getSourceUserId(), request.getCurrency());
        if (sourceWalletAccount.getBalance().compareTo(amount) < 0) {
            return STATUS_INSUFFICIENT_FUNDS;
        }
        transferMoney(sourceWalletAccount, request.getTargetUserId(), amount, request.getCurrency());
        return STATUS_SUCCESS;
    }

    private BigDecimal parseAmount(String amount, Long paymentId) {
        try {
            return new BigDecimal(amount);
        } catch (NumberFormatException e) {
            log.error("[WalletService] Invalid amount '{}' for paymentId={}", amount, paymentId, e);
            return null;
        }
    }

    //TODO: it should become atomic
    private void transferMoney(WalletAccount source, long targetId, BigDecimal amount, String currency) {
        source.setBalance(source.getBalance().subtract(amount));
        walletAccountPort.save(source);
        WalletAccount target = getOrCreateAccount(targetId, currency);
        target.setBalance(target.getBalance().add(amount));
        walletAccountPort.save(target);
    }

    private WalletAccount getOrCreateAccount(long userId, String currency) {
        return walletAccountPort
                .findByUserIdAndCurrency(userId, currency)
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
