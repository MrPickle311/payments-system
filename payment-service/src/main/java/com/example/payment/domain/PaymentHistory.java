package com.example.payment.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.jmolecules.ddd.annotation.Entity;

import java.time.ZoneId;
import java.time.ZonedDateTime;

@ToString
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
public class PaymentHistory {

    private Long id;
    private Long paymentId;

    @Builder.Default
    private String region = "ROOT";

    private String fromState;
    private String toState;
    private String event;

    @Builder.Default
    private ZonedDateTime timestamp = ZonedDateTime.now(ZoneId.systemDefault());
}
