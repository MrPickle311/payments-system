package com.example.fee.fee.domain;

import com.example.payments.common.sharedkernel.Money;
import org.jmolecules.ddd.annotation.ValueObject;

@ValueObject
public record FeeBreakdown(Money totalFee) {}
