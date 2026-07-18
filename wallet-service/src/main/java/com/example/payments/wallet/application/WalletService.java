package com.example.payments.wallet.application;

import com.example.payments.wallet.domain.WalletAccount;
import com.example.payments.wallet.application.port.WalletAccountPort;
import com.example.payments.wallet.grpc.DebitRequest;
import io.micrometer.observation.annotation.Observed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static com.example.payments.wallet.common.WalletConstants.STATUS_INSUFFICIENT_FUNDS;
import static com.example.payments.wallet.common.WalletConstants.STATUS_SUCCESS;

@Slf4j
@Service
@RequiredArgsConstructor
public class WalletService {

  public static final BigDecimal DEFAULT_MOCK_BALANCE = new BigDecimal("1000.00");
  private final WalletAccountPort walletAccountPort;

  @Transactional
  @Observed(name = "debit-between-users")
  public String debitBetweenUsers(DebitRequest req) {
    BigDecimal dec = parseAmount(req.getAmount(), req.getPaymentId());
    if (dec == null) {
      return STATUS_INSUFFICIENT_FUNDS;
    }
    WalletAccount sourceWalletAccount = getOrCreateAccount(req.getSourceUserId(), req.getCurrency());
    if (sourceWalletAccount.getBalance().compareTo(dec) < 0) {
      return STATUS_INSUFFICIENT_FUNDS;
    }
    transferMoney(sourceWalletAccount, req.getTargetUserId(), dec, req.getCurrency());
    return STATUS_SUCCESS;
  }

  private BigDecimal parseAmount(String amount, Long paymentId) {
    try {
      return new BigDecimal(amount);
    } catch (NumberFormatException e) {
      log.error("[WalletService] Invalid amount '{}' for paymentId={}", amount, paymentId,e);
      return null;
    }
  }

  private void transferMoney(WalletAccount source, long tgtId, BigDecimal amt, String cur) {
    source.setBalance(source.getBalance().subtract(amt));
    walletAccountPort.save(source);
    WalletAccount target = getOrCreateAccount(tgtId, cur);
    target.setBalance(target.getBalance().add(amt));
    walletAccountPort.save(target);
  }

  private WalletAccount getOrCreateAccount(long userId, String currency) {
    return walletAccountPort.findByUserIdAndCurrency(userId, currency)
        .orElseGet(() -> createMockAccount(userId, currency));
  }

  private WalletAccount createMockAccount(long userId, String currency) {
    WalletAccount newAccount = WalletAccount.builder().id(userId).userId(userId)
        .balance(DEFAULT_MOCK_BALANCE).currency(currency).build();
    return walletAccountPort.save(newAccount);
  }
}
