import os

base_dir = "/home/damian/sandbox/statemachine-payments/src/main/java"
test_dir = "/home/damian/sandbox/statemachine-payments/src/test/java"

def fix_file(filepath):
    with open(filepath, 'r') as f:
        content = f.read()

    # FIX double packages
    content = content.replace('com.example.payments.payment.infrastructure.payment.infrastructure.repository', 'com.example.payments.payment.infrastructure.repository')
    content = content.replace('com.example.payments.payment.domain.payment.domain', 'com.example.payments.payment.domain')
    content = content.replace('com.example.payments.payment.domain.InvalidTransitionException', 'com.example.payments.payment.domain.exception.InvalidTransitionException')
    content = content.replace('com.example.payments.payment.domain.PaymentNotFoundException', 'com.example.payments.payment.domain.exception.PaymentNotFoundException')

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
