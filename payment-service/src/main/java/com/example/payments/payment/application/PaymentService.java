package com.example.payments.payment.application;

import com.example.payments.fx.FxDetails;
import com.example.payments.fx.FxService;
import com.example.payments.payment.application.dto.CreatePaymentRequest;
import com.example.payments.payment.domain.Payment;
import com.example.payments.payment.domain.PaymentConstants;
import com.example.payments.payment.domain.PaymentHistory;
import com.example.payments.payment.domain.PaymentHistoryRepository;
import com.example.payments.payment.domain.PaymentNotFoundException;
import com.example.payments.payment.domain.PaymentRepository;
import com.example.payments.payment.domain.enums.PaymentEvent;
import com.example.payments.payment.domain.enums.PaymentState;
import io.micrometer.observation.annotation.Observed;
import io.micrometer.tracing.annotation.SpanTag;

import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.payments.fee.application.FeeCalculationService;

import static com.example.payments.payment.domain.enums.PaymentState.NEW;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

  private final PaymentRepository paymentRepository;
  private final PaymentHistoryRepository paymentHistoryRepository;
  private final FxService fxService;
  private final PaymentSagaOrchestrator sagaOrchestrator;

  @Transactional
  @Observed(name = "create-payment")
  public Payment createPayment(CreatePaymentRequest request) {
    String sourceCurrency = request.getSourceCurrency() != null ? request.getSourceCurrency() : request.getCurrency();
    FxDetails fxDetails = fxService.calculateFx(request.getAmount(), sourceCurrency, request.getCurrency());
    Payment payment = Payment.builder() //TODO: move to mapper
            .transactionId(request.getTransactionId())
            .money(request.getMoney())
            .state(NEW.name())
            .sourceCurrency(fxDetails.sourceCurrency())
            .sourceAmount(fxDetails.sourceAmount())
            .exchangeRate(fxDetails.exchangeRate())
            .build();
    payment.registerCreationEvent();
    payment = paymentRepository.save(payment);
    saveInitialHistory(payment);
    return payment;
  }

  private void saveInitialHistory(Payment payment) {
    paymentHistoryRepository.save(PaymentHistory.builder()//TODO: move to mapper
            .paymentId(payment.getId())
        .fromState(PaymentConstants.INITIAL_FROM_STATE)
            .toState(NEW.name())
        .event(PaymentConstants.EVENT_CREATED)
            .build());
  }

  @Transactional(readOnly = true)
  @Observed(name = "get-payment")
  public Payment getPayment(@SpanTag("payment.id") Long paymentId) {
    return paymentRepository.findById(paymentId)
        .orElseThrow(() -> new PaymentNotFoundException(paymentId));
  }

  @Transactional(readOnly = true)
  public Optional<Payment> findByTransactionId(String transactionId) {
    return paymentRepository.findByTransactionId(transactionId);
  }

  @Transactional(readOnly = true)
  @Observed(name = "get-payment-history")
  public List<PaymentHistory> getPaymentHistory(@SpanTag("payment.id") Long paymentId) {
    if (!paymentRepository.existsById(paymentId)) {
      throw new PaymentNotFoundException(paymentId);
    }
    return paymentHistoryRepository.findByPaymentIdOrderByTimestampAsc(paymentId);
  }

  public Payment processEvent(Long paymentId, PaymentEvent event) {
    return sagaOrchestrator.processEvent(paymentId, event);
  }
}

