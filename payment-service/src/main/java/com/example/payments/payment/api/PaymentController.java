package com.example.payments.payment.api;

import com.example.payments.fee.application.FeeCalculationService;
import com.example.payments.payment.api.generated.PaymentsApi;
import com.example.payments.payment.application.PaymentService;
import com.example.payments.payment.api.model.Payment;
import com.example.payments.payment.api.model.PaymentFee;
import com.example.payments.payment.api.model.PaymentHistory;
import com.example.payments.payment.application.dto.CreatePaymentRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PaymentController implements PaymentsApi {

    private final PaymentService paymentService;
    private final FeeCalculationService feeCalculationService;
    private final PaymentMapper paymentMapper;

    @Override
    public ResponseEntity<Payment> createPayment(
            final com.example.payments.payment.api.model.CreatePaymentRequest request) {

        final CreatePaymentRequest appRequest = new CreatePaymentRequest(
                request.getTransactionId(),
                request.getAmount(),
                request.getCurrency()
        );

        final com.example.payments.payment.domain.Payment created =
                paymentService.createPayment(appRequest);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(paymentMapper.toApi(created));
    }

    @Override
    public ResponseEntity<Payment> getPayment(final Long id) {
        return ResponseEntity.ok(paymentMapper.toApi(paymentService.getPayment(id)));
    }

    @Override
    public ResponseEntity<PaymentFee> getPaymentFee(final Long id) {
        return ResponseEntity.ok(
                paymentMapper.toApi(feeCalculationService.getFee(id)));
    }

    @Override
    public ResponseEntity<List<PaymentHistory>> getPaymentHistory(final Long id) {
        final List<PaymentHistory> history = paymentService.getPaymentHistory(id).stream()
                .map(paymentMapper::toApi)
                .toList();
        return ResponseEntity.ok(history);
    }
}
