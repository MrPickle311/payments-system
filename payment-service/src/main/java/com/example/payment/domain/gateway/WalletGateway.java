package com.example.payment.domain.gateway;

import com.example.payments.wallet.grpc.DebitResponse;

public interface WalletGateway {
    DebitResponse debit(WalletDebitCommand command);
}
