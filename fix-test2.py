import os

filepath = "/home/damian/sandbox/statemachine-payments/src/test/java/com/example/payments/config/GeneratedStateMachineConfigTest.java"

with open(filepath, 'r') as f:
    content = f.read()

content = content.replace("FeeCalculationService.FeeBreakdown", "FeeBreakdown")

if 'import com.example.payments.fee.domain.FeeBreakdown;' not in content:
    content = content.replace('import com.example.payments.fee.application.FeeCalculationService;', 'import com.example.payments.fee.application.FeeCalculationService;\nimport com.example.payments.fee.domain.FeeBreakdown;')

with open(filepath, 'w') as f:
    f.write(content)

# Move test file to its new folder
os.makedirs("/home/damian/sandbox/statemachine-payments/src/test/java/com/example/payments/payment/infrastructure/config", exist_ok=True)
os.rename(filepath, "/home/damian/sandbox/statemachine-payments/src/test/java/com/example/payments/payment/infrastructure/config/GeneratedStateMachineConfigTest.java")

