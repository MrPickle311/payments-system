package com.example.payments.payment.application;

import com.example.payments.payment.domain.Payment;
import com.example.payments.payment.domain.PaymentRepository;
import com.example.payments.payment.domain.enums.PaymentEvent;
import com.example.payments.payment.domain.enums.PaymentState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class PaymentTimeoutSchedulerTest {

  @Mock
  private PaymentRepository paymentRepository;
  @Mock
  private PaymentService paymentService;

  private PaymentTimeoutScheduler scheduler;

  @BeforeEach
  void setUp() {
    scheduler = new PaymentTimeoutScheduler(paymentRepository, paymentService, 60);
  }

  @Test
  void testCheckPendingTimeoutsTriggerReject() {
    Payment expired = Payment.builder().id(1L).state(PaymentState.MANUAL_REVIEW.name())
        .createdAt(LocalDateTime.now().minusSeconds(120)).build();
    Payment active = Payment.builder().id(2L).state(PaymentState.MANUAL_REVIEW.name())
        .createdAt(LocalDateTime.now().minusSeconds(10)).build();

    when(paymentRepository.findByState(PaymentState.MANUAL_REVIEW.name()))
        .thenReturn(List.of(expired, active));

    scheduler.checkPendingTimeouts();

    verify(paymentService).processEvent(1L, PaymentEvent.ANALYST_REJECT);
    verifyNoMoreInteractions(paymentService);
  }
}
