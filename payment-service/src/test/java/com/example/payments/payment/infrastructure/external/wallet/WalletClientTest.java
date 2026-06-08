package com.example.payments.payment.infrastructure.external.wallet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.example.payments.common.dto.DebitRequest;
import com.example.payments.common.dto.DebitResponse;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

@ExtendWith(MockitoExtension.class)
class WalletClientTest {

  private static final String BASE_URL = "http://localhost:8080";
  private static final String DEBIT_URL = "http://localhost:8080/wallets/debit";
  private static final String STATUS_SUCCESS = "SUCCESS";
  private static final String AMOUNT = "100.00";
  private static final String CURRENCY = "USD";

  @Mock
  private WalletProperties walletProperties;

  @Mock
  private RestTemplate restTemplate;

  @InjectMocks
  private WalletClient walletClient;

  @Test
  void debitSuccess() {
    when(walletProperties.getUrl()).thenReturn(BASE_URL);
    DebitResponse mockResp = DebitResponse.builder().status(STATUS_SUCCESS).build();
    when(
        restTemplate.postForObject(eq(DEBIT_URL), any(DebitRequest.class), eq(DebitResponse.class)))
            .thenReturn(mockResp);

    DebitResponse response = walletClient.debit(1L, new BigDecimal(AMOUNT), CURRENCY);

    assertEquals(STATUS_SUCCESS, response.getStatus());
  }

  @Test
  void debitFailure() {
    when(walletProperties.getUrl()).thenReturn(BASE_URL);
    when(
        restTemplate.postForObject(eq(DEBIT_URL), any(DebitRequest.class), eq(DebitResponse.class)))
            .thenThrow(new RuntimeException("Connection refused"));

    DebitResponse response = walletClient.debit(1L, new BigDecimal(AMOUNT), CURRENCY);

    assertEquals("FAILED", response.getStatus());
  }
}
