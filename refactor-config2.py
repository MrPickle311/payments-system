import os

filepath = "/home/damian/sandbox/statemachine-payments/src/main/java/com/example/payments/payment/infrastructure/config/PaymentStateMachinePersister.java"

with open(filepath, 'r') as f:
    content = f.read()

content = content.replace("payment.getAmount()", "payment.getMoney().getAmount()")
content = content.replace("payment.getCurrency()", "payment.getMoney().getCurrency()")

with open(filepath, 'w') as f:
    f.write(content)

filepath = "/home/damian/sandbox/statemachine-payments/src/main/java/com/example/payments/payment/infrastructure/config/StateMachineConfig.java"

with open(filepath, 'r') as f:
    content = f.read()

if 'import com.example.payments.fee.domain.FeeBreakdown;' not in content:
    content = content.replace('import com.example.payments.shared.domain.Money;', 'import com.example.payments.shared.domain.Money;\nimport com.example.payments.fee.domain.FeeBreakdown;')

content = content.replace("FeeCalculationService.FeeBreakdown", "FeeBreakdown")

with open(filepath, 'w') as f:
    f.write(content)
