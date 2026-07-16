package com.example.payments.payment.api;

import com.example.payments.payment.api.model.ApiPayment;
import com.example.payments.payment.api.model.ApiPaymentHistory;
import com.example.payments.payment.api.model.ApiCreatePaymentRequest;


import com.example.payments.payment.domain.Payment;


import com.example.payments.payment.api.generated.PaymentsApi;
import com.example.payments.payment.application.PaymentService;
import com.example.payments.fraud.application.FraudCheckService;
import com.example.payments.payment.application.dto.CreatePaymentRequest;
import com.example.payments.payment.domain.enums.PaymentEvent;
import com.example.payments.payment.domain.enums.PaymentState;

import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@SuppressWarnings("checkstyle:AbbreviationAsWordInName")
@RestController
@RequiredArgsConstructor
public class PaymentController implements PaymentsApi {

  private final PaymentService paymentService;
  private final PaymentMapper paymentMapper;
  private final FraudCheckService fraudCheckService;

  @Override
  public ResponseEntity<ApiPayment> createPayment(final ApiCreatePaymentRequest request) {
    final Optional<Payment> existing =
        paymentService.findByTransactionId(request.getTransactionId());
    if (existing.isPresent()) {
      return ResponseEntity.ok(paymentMapper.toApi(existing.get()));
    }
    return tryCreatePayment(request);
  }

  private ResponseEntity<ApiPayment> tryCreatePayment(ApiCreatePaymentRequest request) {
    try {
      final CreatePaymentRequest appRequest = new CreatePaymentRequest(request.getTransactionId(),
          request.getAmount(), request.getCurrency()); //TODO: move to mapper
      final Payment payment = paymentService.createPayment(appRequest);
      return ResponseEntity.status(HttpStatus.CREATED).body(paymentMapper.toApi(payment)); //TODO: move to mapper
    } catch (DataIntegrityViolationException e) { //TODO: move to global exception handler
      final Payment ex =
          paymentService.findByTransactionId(request.getTransactionId()).orElseThrow(() -> e);
      return ResponseEntity.ok(paymentMapper.toApi(ex));
    }
  }

  @Override
  public ResponseEntity<ApiPayment> getPayment(final Long id) {
    return ResponseEntity.ok(paymentMapper.toApi(paymentService.getPayment(id)));
  }

  @Override
  public ResponseEntity<List<ApiPaymentHistory>> getPaymentHistory(final Long id) {
    final List<ApiPaymentHistory> history = paymentService.getPaymentHistory(id).stream()

        .map(paymentMapper::toApi).toList();
    return ResponseEntity.ok(history);
  }

  @PostMapping("/api/payments/{id}/refunds")
  public ResponseEntity<ApiPayment> refundPayment(@PathVariable("id") final Long id,
      @RequestBody final RefundRequest request) {//TODO: unused refund request
    final Payment payment = paymentService.getPayment(id);
    if (!PaymentState.COMPLETED.name().equalsIgnoreCase(payment.getState())) {
      throw new IllegalStateException("Payment not COMPLETED");
    }
    final Payment refunded = paymentService.processEvent(id, PaymentEvent.REFUND);
    return ResponseEntity.ok(paymentMapper.toApi(refunded));
  }

  @PostMapping("/api/kyc/auto-approve")
  public ResponseEntity<String> autoApproveKyc() {
    fraudCheckService.autoApproveKyc(1L);
    return ResponseEntity.ok("KYC approved for Payer 1L. Segment upgraded to STANDARD.");
  }

  @PostMapping("/api/kyc/onboard")
  public ResponseEntity<String> onboardUser(@RequestBody final OnboardRequest request) {
    fraudCheckService.onboardPayer(request.payerId());
    return ResponseEntity.ok("User onboarded successfully. Current segment: BASIC, KYC Status: PENDING.");
  }

}
