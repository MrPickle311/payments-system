package com.example.payments.fee.domain;

import com.example.payments.payment.domain.Money;
import org.jmolecules.ddd.annotation.ValueObject;

@ValueObject
public record FeeBreakdown(
    Money grossAmount,
    Money percentageFee,
    Money flatFee,
    Money totalFee,
    Money netAmount
) {}
