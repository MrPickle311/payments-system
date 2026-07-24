package com.example.payment.application.service;

import static com.example.payment.domain.enums.PaymentEvent.INITIATE;
import static com.example.payment.domain.enums.PaymentState.NEW;

import com.example.payment.application.dto.CreatePaymentRequest;
import com.example.payment.application.mapper.PaymentApplicationMapper;
import com.example.payment.domain.InvalidTransitionException;
import com.example.payment.domain.Payment;
import com.example.payment.domain.PaymentConstants;
import com.example.payment.domain.PaymentHistory;
import com.example.payment.domain.PaymentHistoryRepository;
import com.example.payment.domain.PaymentNotFoundException;
import com.example.payment.domain.PaymentRepository;
import com.example.payment.domain.enums.PaymentEvent;
import com.example.payment.infrastructure.statemachine.PaymentStateMachineManager;
import io.micrometer.observation.annotation.Observed;
import io.micrometer.tracing.annotation.SpanTag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    public static final String ROOT = "ROOT";

    private final PaymentRepository paymentRepository;
    private final PaymentHistoryRepository paymentHistoryRepository;
    private final PaymentApplicationMapper paymentApplicationMapper;
    private final PaymentStateMachineManager stateMachineManager;

    @Transactional
    @Observed(name = "create-payment")
    public Payment createPayment(CreatePaymentRequest request) {
        Payment payment = paymentApplicationMapper.toNewDomainPayment(request);

        payment.registerCreationEvent();
        payment = paymentRepository.save(payment);

        paymentHistoryRepository.save(PaymentHistory.builder()
                .paymentId(payment.getId())
                .region(ROOT)
                .fromState(PaymentConstants.INITIAL_FROM_STATE)
                .toState(NEW.name())
                .event(PaymentConstants.EVENT_CREATED)
                .build());

        log.info("[Service] Created payment id={} transactionId={}", payment.getId(), payment.getTransactionId());
        return payment;
    }

  @Transactional(noRollbackFor = InvalidTransitionException.class)
  @Observed(name = "initiate-payment")
  public void initiatePayment(Long paymentId) {
    processEvent(paymentId, INITIATE);
  }

  @Observed(name = "process-payment-event")
  public Payment processEvent(@SpanTag("payment.id") Long paymentId, PaymentEvent event) {
    Payment payment = paymentRepository.findById(paymentId)
        .orElseThrow(() -> new PaymentNotFoundException(paymentId));
    log.info("[Service] Processing event={} for payment={} (currentState={})", event, paymentId,
        payment.getState());
    stateMachineManager.execute(payment, event);
    return savePayment(paymentId, payment);
  }

    private Payment savePayment(Long paymentId, Payment payment) {
        Payment saved = paymentRepository.save(payment);
        log.info("[Service] Payment {} transitioned to state {}", paymentId, saved.getState());
        return saved;
    }

    @Transactional(readOnly = true)
    @Observed(name = "get-payment")
    public Payment getPayment(@SpanTag("payment.id") Long paymentId) {
        return paymentRepository.findById(paymentId).orElseThrow(() -> new PaymentNotFoundException(paymentId));
    }

  @Transactional(readOnly = true)
  @Observed(name = "get-payment-history")
  public List<PaymentHistory> getPaymentHistory(@SpanTag("payment.id") Long paymentId) {
    if (!paymentRepository.existsById(paymentId)) {
      throw new PaymentNotFoundException(paymentId);
    }
    return paymentHistoryRepository.findByPaymentIdOrderByTimestampAsc(paymentId);
  }
}
