package com.example.payments.payment.infrastructure.config;

import com.example.payments.fee.application.FeeCalculationPort;
import com.example.payments.fraud.application.FraudCheckPort;
import com.example.payments.payment.application.saga.PaymentProcessingSaga;
import com.example.payments.payment.domain.enums.PaymentEvent;
import com.example.payments.payment.domain.enums.PaymentState;
import com.example.payments.payment.infrastructure.external.ledger.LedgerPublisher;
import com.example.payments.payment.infrastructure.external.wallet.WalletClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.statemachine.ExtendedState;
import org.springframework.statemachine.StateContext;

import java.time.LocalDateTime;

import static com.example.payments.payment.domain.PaymentConstants.IS_RESTORING;
import static com.example.payments.payment.domain.PaymentConstants.PAYMENT_CREATED_AT;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StateMachineConfigTest {

  @Mock
  private FraudCheckPort fraudCheckService;
  @Mock
  private FeeCalculationPort feeCalculationService;
  @Mock
  private WalletClient walletClient;
  @Mock
  private LedgerPublisher ledgerPublisher;

  @Mock
  private PaymentProcessingSaga paymentProcessingSaga;

  private StateMachineConfig config;

  @BeforeEach
  void setUp() {
    config = new StateMachineConfig(feeCalculationService, walletClient, ledgerPublisher,
        paymentProcessingSaga);
  }

  @Test
  void testRefundWindowGuard() {
    StateContext<PaymentState, PaymentEvent> context = mock(StateContext.class);
    ExtendedState extendedState = mock(ExtendedState.class);
    when(context.getExtendedState()).thenReturn(extendedState);
    when(extendedState.get(PAYMENT_CREATED_AT, LocalDateTime.class))
        .thenReturn(LocalDateTime.now().minusDays(10));
    assertTrue(config.refundWindowGuard().evaluate(context));
    when(extendedState.get(PAYMENT_CREATED_AT, LocalDateTime.class))
        .thenReturn(LocalDateTime.now().minusDays(40));
    assertFalse(config.refundWindowGuard().evaluate(context));
  }

  @Test
  void testCompletedEntryActionRestoring() {
    StateContext<PaymentState, PaymentEvent> context = mock(StateContext.class);
    ExtendedState extendedState = mock(ExtendedState.class);
    when(context.getExtendedState()).thenReturn(extendedState);
    when(extendedState.get(IS_RESTORING, Boolean.class)).thenReturn(true);

    config.completedEntryAction().apply(context).block();
    verify(paymentProcessingSaga, never()).completedEntry(any());
  }
}
