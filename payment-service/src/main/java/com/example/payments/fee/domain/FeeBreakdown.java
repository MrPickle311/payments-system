package com.example.payments.fee.domain;

import com.example.payments.common.domain.Money;

@org.jmolecules.ddd.annotation.ValueObject
public record FeeBreakdown(
    Money grossAmount,
    Money percentageFee,
    Money flatFee,
    Money totalFee,
    Money netAmount
) {}
