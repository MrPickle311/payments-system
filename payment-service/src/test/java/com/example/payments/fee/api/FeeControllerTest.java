package com.example.payments.fee.api;

import com.example.payments.fee.application.FeeCalculationService;
import com.example.payments.fee.application.FeeCalculationPort.FeeDto;
import com.example.payments.payment.api.model.ApiPaymentFee;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeeControllerTest {

  @Mock
  private FeeCalculationService feeCalculationService;

  private FeeController feeController;

  @BeforeEach
  void setUp() {
    feeController = new FeeController(feeCalculationService, new FeeMapperImpl());
  }

  @Test
  void testGetPaymentFee() {
    FeeDto fee = new FeeDto(1L, 2L, new BigDecimal("100.00"), null, null, null, null, "USD");

    when(feeCalculationService.getFee(2L)).thenReturn(fee);

    ResponseEntity<ApiPaymentFee> response = feeController.getPaymentFee(2L);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    assertEquals(1L, response.getBody().getId());
    assertEquals(2L, response.getBody().getPaymentId());
  }
}
