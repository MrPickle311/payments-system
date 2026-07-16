package com.example.payments.payment.infrastructure.external.wallet;

import lombok.extern.slf4j.Slf4j;

import com.example.payments.common.dto.DebitRequest;
import com.example.payments.common.dto.DebitResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;


@Slf4j
@Service
@RequiredArgsConstructor
public class WalletClient {

  public static final String WALLET_DEBIT_PATH = "/wallets/debit";
  private final WalletProperties walletProperties;
  private final RestTemplate restTemplate;

  public DebitResponse debit(Long paymentId, BigDecimal amount, String currency) {
    return debit(paymentId, 1L, amount, currency);
  }

  public DebitResponse debit(Long paymentId, Long walletId, BigDecimal amount, String currency) {
    log.info("[WalletClient] Calling Wallet Service: paymentId={} walletId={} amount={} {}",
        paymentId, walletId, amount, currency);
    DebitRequest request = DebitRequest.builder().paymentId(paymentId).walletId(walletId)
        .amount(amount).currency(currency).build();
    try {
      return restTemplate.postForObject(walletProperties.getUrl() + WALLET_DEBIT_PATH, request,
          DebitResponse.class);
    } catch (Exception e) {
      log.error("[WalletClient] ERROR calling Wallet Service: {}", e.getMessage());
      return DebitResponse.builder().status("FAILED").build();
    }
  }
}
