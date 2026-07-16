package com.example.payments.payment.api;



import com.example.payments.payment.application.PaymentService;
import com.example.payments.fraud.application.FraudCheckService;
import com.example.payments.payment.domain.enums.PaymentEvent;
import com.example.payments.sharedkernel.Money;
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
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;

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
  @Mock
  private FraudCheckService fraudCheckService;

  private PaymentController paymentController;

  @BeforeEach
  void setUp() {
    paymentController = new PaymentController(paymentService, new PaymentMapperImpl(), fraudCheckService);
  }

  private static final String TX123 = "TX123";
  private static final String HUNDRED = "100.00";
  private static final String USD = "USD";
  private static final String NEW_STATE = "NEW";
  private static final String REFUNDED_STATE = "REFUNDED";

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

  @Test
  void testCreatePaymentIdempotent() {
    ApiCreatePaymentRequest request = createRequest();
    Payment payment = createPayment();
    when(paymentService.findByTransactionId(TX123)).thenReturn(Optional.of(payment));
    ResponseEntity<ApiPayment> response = paymentController.createPayment(request);
    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    assertEquals(TX123, response.getBody().getTransactionId());
  }

  @Test
  void testCreatePaymentConcurrency() {
    ApiCreatePaymentRequest request = createRequest();
    Payment payment = createPayment();
    when(paymentService.findByTransactionId(TX123)).thenReturn(Optional.empty())
        .thenReturn(Optional.of(payment));
    when(paymentService.createPayment(any())).thenThrow(new DataIntegrityViolationException("dup"));
    ResponseEntity<ApiPayment> response = paymentController.createPayment(request);
    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
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
  void testRefundPayment() {
    Payment payment = createPayment();
    payment.setState("COMPLETED");
    Payment refunded = createPayment();
    refunded.setState(REFUNDED_STATE);
    when(paymentService.getPayment(1L)).thenReturn(payment);
    when(paymentService.processEvent(1L, PaymentEvent.REFUND)).thenReturn(refunded);
    ResponseEntity<ApiPayment> response = paymentController.refundPayment(1L,
        new RefundRequest(new BigDecimal(HUNDRED), "reason"));
    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    assertEquals(REFUNDED_STATE, response.getBody().getState());
  }

  private PaymentHistory createHistory() {
    return PaymentHistory.builder().id(1L).paymentId(2L).event("CREATED").build();
  }

  @Test
  void testAutoApproveKyc() {
    ResponseEntity<String> response = paymentController.autoApproveKyc();
    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals("KYC approved for Payer 1L. Segment upgraded to STANDARD.", response.getBody());
  }

  @Test
  void testOnboardUser() {
    var req = new OnboardRequest(2L, "John", "john@example.com");
    ResponseEntity<String> response = paymentController.onboardUser(req);
    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals("User onboarded successfully. Current segment: BASIC, KYC Status: PENDING.", response.getBody());
  }
}
