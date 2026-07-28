package com.example.payment.domain.gateway;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WalletDebitCommand {
    private Long paymentId;
    private Long sourceUserId;
    private Long targetUserId;
    private String amount;
    private String currency;
    private String idempotencyKey;
}
