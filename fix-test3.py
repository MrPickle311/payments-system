import os

filepath = "/home/damian/sandbox/statemachine-payments/src/test/java/com/example/payments/payment/infrastructure/config/GeneratedStateMachineConfigTest.java"

with open(filepath, 'r') as f:
    content = f.read()

content = content.replace(
    '''                        new BigDecimal("100.00"),
                        new BigDecimal("2.9000"),
                        new BigDecimal("0.30"),
                        new BigDecimal("3.2000"),
                        new BigDecimal("96.8000")''',
    '''                        Money.of("100.00", "USD"),
                        Money.of("2.9000", "USD"),
                        Money.of("0.30", "USD"),
                        Money.of("3.2000", "USD"),
                        Money.of("96.8000", "USD")'''
)

with open(filepath, 'w') as f:
    f.write(content)

