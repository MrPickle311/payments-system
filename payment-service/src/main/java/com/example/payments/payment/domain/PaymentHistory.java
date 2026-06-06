package com.example.payments.payment.domain;

import lombok.Getter;
import lombok.ToString;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

/**
 * Immutable audit record.
 */
@ToString
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@org.jmolecules.ddd.annotation.Entity
public class PaymentHistory {

    private Long id;
    private Long paymentId;
    private String fromState;
    private String toState;
    private String event;
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
}
