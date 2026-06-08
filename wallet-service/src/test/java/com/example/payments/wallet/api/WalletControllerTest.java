package com.example.payments.wallet.api;

import com.example.payments.common.dto.DebitRequest;
import com.example.payments.common.dto.DebitResponse;
import com.example.payments.wallet.application.WalletService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static com.example.payments.wallet.common.WalletConstants.STATUS_SUCCESS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WalletControllerTest {

  @Mock
  private WalletService walletService;

  @InjectMocks
  private WalletController walletController;

  private static final String REFERENCE_IDENTIFIER = "REF-12345";

  @Test
  void testDebit() {
    DebitRequest request = DebitRequest.builder().paymentId(1L).amount(new BigDecimal("100.00"))
        .currency("USD").build();
    DebitResponse mockResponse =
        DebitResponse.builder().status(STATUS_SUCCESS).referenceId(REFERENCE_IDENTIFIER).build();

    when(walletService.debit(request)).thenReturn(mockResponse);

    DebitResponse response = walletController.debit(request);

    assertEquals(STATUS_SUCCESS, response.getStatus());
    assertEquals(REFERENCE_IDENTIFIER, response.getReferenceId());
  }
}
