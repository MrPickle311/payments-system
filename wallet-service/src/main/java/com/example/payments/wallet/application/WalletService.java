package com.example.payments.wallet.application;

import com.example.payments.common.dto.DebitRequest;
import com.example.payments.common.dto.DebitResponse;
import com.example.payments.wallet.domain.WalletAccount;
import com.example.payments.wallet.application.port.WalletAccountPort;
import io.micrometer.observation.annotation.Observed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static com.example.payments.wallet.common.WalletConstants.REF_PREFIX;
import static com.example.payments.wallet.common.WalletConstants.STATUS_INSUFFICIENT_FUNDS;
import static com.example.payments.wallet.common.WalletConstants.STATUS_SUCCESS;

@Slf4j
@Service
@RequiredArgsConstructor
public class WalletService {

  public static final long DEFAULT_USER_IDENTIFIER = 1L;
  public static final BigDecimal DEFAULT_MOCK_BALANCE = new BigDecimal("1000.00");
  private final WalletAccountPort walletAccountPort;

  @Transactional
  @Observed(name = "debit-wallet")
  public DebitResponse debit(DebitRequest request) {
    log.info("[WalletService] Processing debit for paymentId={} amount={} {}",
        request.getPaymentId(), request.getAmount(), request.getCurrency());
    WalletAccount account = getOrCreateAccount(request.getCurrency());
    if (hasInsufficientFunds(account, request.getAmount())) {
      return buildInsufficientFundsResponse(account, request.getAmount());
    }
    return processSuccessfulDebit(account, request);
  }

  private WalletAccount getOrCreateAccount(String currency) {
    return walletAccountPort.findByUserIdAndCurrency(DEFAULT_USER_IDENTIFIER, currency)
        .orElseGet(() -> createMockAccount(currency));
  }

  private WalletAccount createMockAccount(String currency) {
    WalletAccount newAccount = WalletAccount.builder().id(DEFAULT_USER_IDENTIFIER)
        .userId(DEFAULT_USER_IDENTIFIER).balance(DEFAULT_MOCK_BALANCE).currency(currency).build();
    return walletAccountPort.save(newAccount);
  }

  private boolean hasInsufficientFunds(WalletAccount account, BigDecimal amount) {
    return account.getBalance().compareTo(amount) < 0;
  }

  private DebitResponse buildInsufficientFundsResponse(WalletAccount account, BigDecimal amount) {
    log.warn("[WalletService] Insufficient funds for account userId={} balance={} requested={}",
        account.getUserId(), account.getBalance(), amount);
    return DebitResponse.builder().status(STATUS_INSUFFICIENT_FUNDS).build();
  }

  private DebitResponse processSuccessfulDebit(WalletAccount account, DebitRequest request) {
    account.setBalance(account.getBalance().subtract(request.getAmount()));
    walletAccountPort.save(account);
    log.info("[WalletService] Debit successful for paymentId={} new balance={}",
        request.getPaymentId(), account.getBalance());
    return DebitResponse.builder().status(STATUS_SUCCESS)
        .referenceId(REF_PREFIX + UUID.randomUUID()).build();
  }
}
