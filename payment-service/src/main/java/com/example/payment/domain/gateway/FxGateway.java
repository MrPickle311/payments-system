package com.example.payment.domain.gateway;

import com.example.payments.fx.grpc.FxResponse;

public interface FxGateway {
    FxResponse processFx(Long paymentId, String amount, String sourceCurrency, String targetCurrency);
}
