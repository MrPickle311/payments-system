package com.example.payment.domain.gateway;

import lombok.*;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class WalletDebitCommand {
    private Long paymentId;
    private Long sourceUserId;
    private Long targetUserId;
    private String amount;
    private String currency;
    private String idempotencyKey;
}
