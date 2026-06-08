package com.example.payments.fee.application;


import com.example.payments.payment.domain.Money;

import java.math.BigDecimal;

public interface FeeCalculationPort {
  FeeBreakdown calculate(Money money);

  void saveSettlement(Long paymentId, Money money);

  FeeDto getFee(Long paymentId);

  record FeeBreakdown(Money grossAmount, Money percentageFee, Money flatFee, Money totalFee, Money netAmount) {}

  record FeeDto(Long id, Long paymentId, BigDecimal grossAmount, BigDecimal percentageFee, BigDecimal flatFee, BigDecimal totalFee, BigDecimal netAmount, String currency) {}
}
