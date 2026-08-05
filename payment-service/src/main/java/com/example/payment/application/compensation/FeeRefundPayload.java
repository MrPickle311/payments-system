package com.example.payment.application.compensation;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class FeeRefundPayload {

    @JsonProperty("paymentId")
    private Long paymentId;

    @JsonProperty("targetUserId")
    private Long targetUserId;

    @JsonProperty("amount")
    private String amount;

    @JsonProperty("currency")
    private String currency;
}
