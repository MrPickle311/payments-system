import os

filepath = "/home/damian/sandbox/statemachine-payments/src/test/java/com/example/payments/config/GeneratedStateMachineConfigTest.java"

with open(filepath, 'r') as f:
    content = f.read()

content = content.replace("import java.math.BigDecimal;", "import java.math.BigDecimal;\nimport com.example.payments.shared.domain.Money;")

content = content.replace("evaluate(anyLong(), any(BigDecimal.class), anyString())", "evaluate(anyLong(), any(Money.class))")
content = content.replace("evaluate(anyLong(), any(), anyString())", "evaluate(anyLong(), any(Money.class))")

content = content.replace("saveSettlement(anyLong(), any(), anyString())", "saveSettlement(anyLong(), any(Money.class))")
content = content.replace("calculate(any(BigDecimal.class))", "calculate(any(Money.class))")

content = content.replace("calculate(new BigDecimal(\"100.00\"))", "calculate(Money.of(\"100.00\", \"USD\"))")
content = content.replace("saveSettlement(1L, new BigDecimal(\"100.00\"), \"USD\")", "saveSettlement(1L, Money.of(\"100.00\", \"USD\"))")

content = content.replace("import com.example.payments.payment.infrastructure.config.StateMachineConfig;", "")
content = content.replace("package com.example.payments.config;", "package com.example.payments.payment.infrastructure.config;")

with open(filepath, 'w') as f:
    f.write(content)
