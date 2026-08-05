package com.example.payment.api;

import static org.springframework.http.HttpStatus.CREATED;

import com.example.payment.api.generated.PaymentsApi;
import com.example.payment.api.model.ApiCreatePaymentRequest;
import com.example.payment.api.model.ApiPayment;
import com.example.payment.api.model.ApiPaymentHistory;
import com.example.payment.application.dto.CreatePaymentRequest;
import com.example.payment.application.service.PaymentService;
import com.example.payment.domain.Payment;
import java.util.List;
import lombok.RequiredArgsConstructor;
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
        final CreatePaymentRequest appRequest = new CreatePaymentRequest(
                request.getTransactionId(),
                request.getAmount(),
                request.getCurrency(),
                request.getSourceUserId(),
                request.getTargetUserId(),
                request.getSourceCurrency(),
                request.getTargetCurrency());

        final Payment created = paymentService.createPayment(appRequest);
        return ResponseEntity.status(CREATED).body(paymentMapper.toApi(created));
    }

    @Override
    public ResponseEntity<ApiPayment> getPayment(final Long id) {
        return ResponseEntity.ok(paymentMapper.toApi(paymentService.getPayment(id)));
    }

    @Override
    public ResponseEntity<List<ApiPaymentHistory>> getPaymentHistory(final Long id) {
        final List<ApiPaymentHistory> history = paymentService.getPaymentHistory(id).stream()
                .map(paymentMapper::toApi)
                .toList();
        return ResponseEntity.ok(history);
    }

    @Override
    public ResponseEntity<Void> executePayment(final Long id) {
        paymentService.executePayment(id);
        return ResponseEntity.accepted().build();
    }
}
