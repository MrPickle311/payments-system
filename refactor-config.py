import os

filepath = "/home/damian/sandbox/statemachine-payments/src/main/java/com/example/payments/payment/infrastructure/config/StateMachineConfig.java"

with open(filepath, 'r') as f:
    content = f.read()

# Add import
if 'import com.example.payments.shared.domain.Money;' not in content:
    content = content.replace('import com.example.payments.fraud.application.FraudCheckService;', 'import com.example.payments.fraud.application.FraudCheckService;\nimport com.example.payments.shared.domain.Money;')

# 1. fraudCheckGuard
content = content.replace(
    '''            BigDecimal amount = context.getExtendedState().get("paymentAmount", BigDecimal.class);
            String currency = context.getExtendedState().get("paymentCurrency", String.class);

            FraudCheckService.FraudResult result =
                    fraudCheckService.evaluate(paymentId, amount, currency);''',
    '''            BigDecimal amount = context.getExtendedState().get("paymentAmount", BigDecimal.class);
            String currency = context.getExtendedState().get("paymentCurrency", String.class);
            Money money = Money.of(amount, currency);

            FraudCheckService.FraudResult result =
                    fraudCheckService.evaluate(paymentId, money);'''
)

# 2. feeCalculationAction
content = content.replace(
    '''            BigDecimal amount = context.getExtendedState().get("paymentAmount", BigDecimal.class);

            FeeCalculationService.FeeBreakdown breakdown = feeCalculationService.calculate(amount);''',
    '''            BigDecimal amount = context.getExtendedState().get("paymentAmount", BigDecimal.class);
            String currency = context.getExtendedState().get("paymentCurrency", String.class);
            Money money = Money.of(amount, currency);

            FeeCalculationService.FeeBreakdown breakdown = feeCalculationService.calculate(money);'''
)
content = content.replace(
    '''            context.getExtendedState().getVariables()
                    .put("processingFee", breakdown.totalFee());
            context.getExtendedState().getVariables()
                    .put("netAmount", breakdown.netAmount());''',
    '''            context.getExtendedState().getVariables()
                    .put("processingFee", breakdown.totalFee().getAmount());
            context.getExtendedState().getVariables()
                    .put("netAmount", breakdown.netAmount().getAmount());'''
)
content = content.replace(
    '''                    amount,
                    breakdown.totalFee(),
                    "2.9",
                    breakdown.flatFee(),
                    breakdown.netAmount());''',
    '''                    amount,
                    breakdown.totalFee().getAmount(),
                    "2.9",
                    breakdown.flatFee().getAmount(),
                    breakdown.netAmount().getAmount());'''
)

# 3. settlementAction
content = content.replace(
    '''            // ── I/O operation 1: persist fee record to the database ────────
            feeCalculationService.saveSettlement(paymentId, amount, currency);''',
    '''            // ── I/O operation 1: persist fee record to the database ────────
            feeCalculationService.saveSettlement(paymentId, Money.of(amount, currency));'''
)

with open(filepath, 'w') as f:
    f.write(content)
