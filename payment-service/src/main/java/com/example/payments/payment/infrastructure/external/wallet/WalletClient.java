package com.example.payments.payment.infrastructure.external.wallet;

import com.example.payments.common.dto.DebitRequest;
import com.example.payments.common.dto.DebitResponse;
import org.springframework.beans.factory.annotation.Value;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;

/**
 * Client to communicate with the Wallet Service via REST API.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WalletClient {

    @Value("${wallet.service.url}")
    private String walletServiceUrl;

    private final RestTemplate restTemplate = new RestTemplate(); // For simplicity, using RestTemplate

    public DebitResponse debit(Long paymentId, BigDecimal amount, String currency) {
        log.info("[PaymentService -> WalletClient] Calling Wallet Service for paymentId={} amount={} {}", 
                paymentId, amount, currency);
        
        DebitRequest request = DebitRequest.builder()
                .paymentId(paymentId)
                .amount(amount)
                .currency(currency)
                .build();
        
        String url = walletServiceUrl + "/wallets/debit"; 
        
        try {
            return restTemplate.postForObject(url, request, DebitResponse.class);
        } catch (Exception e) {
            log.error("[WalletClient] ERROR calling Wallet Service: {}", e.getMessage());
            return DebitResponse.builder().status("FAILED").build();
        }
    }
}
