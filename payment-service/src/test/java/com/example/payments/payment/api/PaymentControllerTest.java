package com.example.payments.payment.api;



import com.example.payments.payment.api.model.ApiPaymentHistory;
import com.example.payments.payment.application.PaymentService;
import com.example.payments.payment.domain.Money;
import com.example.payments.payment.domain.Payment;
import com.example.payments.payment.domain.PaymentHistory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import com.example.payments.payment.api.model.ApiCreatePaymentRequest;
import com.example.payments.payment.api.model.ApiPayment;

@ExtendWith(MockitoExtension.class)
class PaymentControllerTest {

  @Mock
  private PaymentService paymentService;

  private PaymentController paymentController;

  @BeforeEach
  void setUp() {
    paymentController = new PaymentController(paymentService, new PaymentMapperImpl());
  }

  private static final String TX123 = "TX123";
  private static final String HUNDRED = "100.00";
  private static final String USD = "USD";
  private static final String NEW_STATE = "NEW";

  @Test
  void testCreatePayment() {
    ApiCreatePaymentRequest request = createRequest();
    Payment payment = createPayment();
    when(paymentService.createPayment(any())).thenReturn(payment);

    ResponseEntity<ApiPayment> response = paymentController.createPayment(request);

    assertEquals(HttpStatus.CREATED, response.getStatusCode());
    assertNotNull(response.getBody());
    assertEquals(1L, response.getBody().getId());
    assertEquals(TX123, response.getBody().getTransactionId());
    assertEquals(NEW_STATE, response.getBody().getState());
  }

  private ApiCreatePaymentRequest createRequest() {
    ApiCreatePaymentRequest req = new ApiCreatePaymentRequest();
    req.setTransactionId(TX123);
    req.setAmount(new BigDecimal(HUNDRED));
    req.setCurrency(USD);
    return req;
  }

  private Payment createPayment() {
    Payment p = new Payment();
    p.setId(1L);
    p.setTransactionId(TX123);
    p.setState(NEW_STATE);
    p.setMoney(Money.of(new BigDecimal(HUNDRED), USD));
    p.setCreatedAt(LocalDateTime.now());
    p.setUpdatedAt(LocalDateTime.now());
    return p;
  }

  @Test
  void testGetPayment() {
    Payment payment = new Payment();
    payment.setId(1L);
    payment.setTransactionId(TX123);

    when(paymentService.getPayment(1L)).thenReturn(payment);

    ResponseEntity<ApiPayment> response = paymentController.getPayment(1L);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    assertEquals(1L, response.getBody().getId());
  }

  @Test
  void testGetPaymentHistory() {
    PaymentHistory history = createHistory();
    when(paymentService.getPaymentHistory(2L)).thenReturn(List.of(history));

    ResponseEntity<List<ApiPaymentHistory>> response = paymentController.getPaymentHistory(2L);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    assertEquals(1, response.getBody().size());
    assertEquals(1L, response.getBody().get(0).getId());
  }

  private PaymentHistory createHistory() {
    return PaymentHistory.builder().id(1L).paymentId(2L).event("CREATED").build();
  }
}
