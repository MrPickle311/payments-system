package com.example.payments.payment.api;

import com.example.payments.payment.api.model.ApiPayment;
import com.example.payments.payment.api.model.ApiPaymentHistory;
import com.example.payments.payment.api.model.ApiCreatePaymentRequest;


import com.example.payments.payment.domain.Payment;


import com.example.payments.payment.api.generated.PaymentsApi;
import com.example.payments.payment.application.PaymentService;
import com.example.payments.payment.application.dto.CreatePaymentRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@SuppressWarnings("checkstyle:AbbreviationAsWordInName")
@RestController
@RequiredArgsConstructor
public class PaymentController implements PaymentsApi {

  private final PaymentService paymentService;
  private final PaymentMapper paymentMapper;

  @Override
  public ResponseEntity<ApiPayment> createPayment(final ApiCreatePaymentRequest request) {

    final CreatePaymentRequest appRequest = new CreatePaymentRequest(request.getTransactionId(),
        request.getAmount(), request.getCurrency());

    final Payment created = paymentService.createPayment(appRequest);
    return ResponseEntity.status(HttpStatus.CREATED).body(paymentMapper.toApi(created));
  }

  @Override
  public ResponseEntity<ApiPayment> getPayment(final Long id) {
    return ResponseEntity.ok(paymentMapper.toApi(paymentService.getPayment(id)));
  }

  @Override
  public ResponseEntity<List<ApiPaymentHistory>> getPaymentHistory(final Long id) {
    final List<ApiPaymentHistory> history =
        paymentService.getPaymentHistory(id).stream().map(paymentMapper::toApi).toList();
    return ResponseEntity.ok(history);
  }
}
