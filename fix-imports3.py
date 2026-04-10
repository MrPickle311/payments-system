import os

base_dir = "/home/damian/sandbox/statemachine-payments/src/main/java"
test_dir = "/home/damian/sandbox/statemachine-payments/src/test/java"

def fix_file(filepath):
    with open(filepath, 'r') as f:
        content = f.read()

    # Revert exception package fix
    content = content.replace('com.example.payments.payment.domain.exception.InvalidTransitionException', 'com.example.payments.payment.domain.InvalidTransitionException')
    content = content.replace('com.example.payments.payment.domain.exception.PaymentNotFoundException', 'com.example.payments.payment.domain.PaymentNotFoundException')

    # Also there was a symbol missing for GeneratedStateMachineConfig in StateMachineConfig.java
    # We moved it to `com.example.payments.payment.infrastructure.config`
    content = content.replace('com.example.payments.config.GeneratedStateMachineConfig', 'com.example.payments.payment.infrastructure.config.GeneratedStateMachineConfig')

    with open(filepath, 'w') as f:
        f.write(content)

for root, _, files in os.walk(base_dir):
    for f in files:
        if f.endswith(".java"):
            fix_file(os.path.join(root, f))
            
for root, _, files in os.walk(test_dir):
    for f in files:
        if f.endswith(".java"):
            fix_file(os.path.join(root, f))
