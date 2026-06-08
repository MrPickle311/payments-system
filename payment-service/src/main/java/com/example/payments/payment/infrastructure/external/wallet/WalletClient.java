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
    log.info(
        "[PaymentService -> WalletClient] Calling Wallet Service for paymentId={} amount={} {}",
        paymentId, amount, currency);

    DebitRequest request =
        DebitRequest.builder().paymentId(paymentId).amount(amount).currency(currency).build();

    String url = walletProperties.getUrl() + WALLET_DEBIT_PATH;

    try {
      return restTemplate.postForObject(url, request, DebitResponse.class);
    } catch (Exception e) {
      log.error("[WalletClient] ERROR calling Wallet Service: {}", e.getMessage());
      return DebitResponse.builder().status("FAILED").build();
    }
  }
}
