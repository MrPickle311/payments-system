package com.example.payments.fee.api;

import com.example.payments.fee.application.FeeCalculationService;

import com.example.payments.payment.api.generated.FeesApi;
import com.example.payments.payment.api.model.ApiPaymentFee;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class FeeController implements FeesApi {

  private final FeeCalculationService feeCalculationService;
  private final FeeMapper feeMapper;

  @Override
  public ResponseEntity<ApiPaymentFee> getPaymentFee(final Long id) {
    var fee = feeCalculationService.getFee(id);
    return ResponseEntity.ok(feeMapper.toApi(fee));
  }
}
