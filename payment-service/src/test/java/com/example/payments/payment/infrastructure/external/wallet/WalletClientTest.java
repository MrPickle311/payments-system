package com.example.payments.payment.infrastructure.external.wallet;

import com.example.payments.common.dto.DebitRequest;
import com.example.payments.common.dto.DebitResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WalletClientTest {

  @Mock
  private WalletProperties walletProperties;

  @Mock
  private RestTemplate restTemplate;

  @InjectMocks
  private WalletClient walletClient;

  @Test
  void debitSuccess() {
    when(walletProperties.getUrl()).thenReturn("http://localhost:8080");
    DebitResponse mockResp = DebitResponse.builder().status("SUCCESS").build();
    when(restTemplate.postForObject(eq("http://localhost:8080/wallets/debit"),
        any(DebitRequest.class), eq(DebitResponse.class))).thenReturn(mockResp);

    DebitResponse response = walletClient.debit(1L, new BigDecimal("100.00"), "USD");

    assertEquals("SUCCESS", response.getStatus());
  }

  @Test
  void debitFailure() {
    when(walletProperties.getUrl()).thenReturn("http://localhost:8080");
    when(restTemplate.postForObject(eq("http://localhost:8080/wallets/debit"),
        any(DebitRequest.class), eq(DebitResponse.class)))
            .thenThrow(new RuntimeException("Connection refused"));

    DebitResponse response = walletClient.debit(1L, new BigDecimal("100.00"), "USD");

    assertEquals("FAILED", response.getStatus());
  }
}
