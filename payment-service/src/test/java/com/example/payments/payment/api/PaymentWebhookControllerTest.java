package com.example.payments.payment.api;

import com.example.payments.payment.domain.enums.PaymentEvent;
import com.example.payments.payment.api.model.ApiWebhookPayload;
import com.example.payments.payment.application.PaymentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentWebhookControllerTest {

  @Mock
  private PaymentService paymentService;

  @InjectMocks
  private PaymentWebhookController controller;

  @Test
  void handlePaymentEvent() {
    ApiWebhookPayload payload = new ApiWebhookPayload();
    payload.setPaymentId(1L);
    payload.setEvent("AUTHORIZE");

    ResponseEntity<Void> response = controller.handlePaymentEvent(payload);

    assertEquals(200, response.getStatusCode().value());
    verify(paymentService).processEvent(1L, PaymentEvent.AUTHORIZE);
  }
}
