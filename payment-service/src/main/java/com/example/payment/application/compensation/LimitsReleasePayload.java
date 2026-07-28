package com.example.payment.application.compensation;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class LimitsReleasePayload {

    @JsonProperty("paymentId")
    private Long paymentId;

    @JsonProperty("sourceUserId")
    private Long sourceUserId;

    @JsonProperty("amount")
    private String amount;

}
