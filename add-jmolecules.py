import os

files_to_update = {
    "src/main/java/com/example/payments/payment/domain/Payment.java": "@org.jmolecules.ddd.annotation.AggregateRoot\npublic class Payment",
    "src/main/java/com/example/payments/fraud/domain/FraudRecord.java": "@org.jmolecules.ddd.annotation.Entity\npublic class FraudRecord",
    "src/main/java/com/example/payments/fee/domain/PaymentFee.java": "@org.jmolecules.ddd.annotation.Entity\npublic class PaymentFee",
    "src/main/java/com/example/payments/payment/domain/PaymentHistory.java": "@org.jmolecules.ddd.annotation.Entity\npublic class PaymentHistory",
    "src/main/java/com/example/payments/shared/domain/Money.java": "@org.jmolecules.ddd.annotation.ValueObject\npublic class Money",
    "src/main/java/com/example/payments/fee/domain/FeeBreakdown.java": "@org.jmolecules.ddd.annotation.ValueObject\npublic record FeeBreakdown",
    "src/main/java/com/example/payments/payment/domain/event/PaymentCreatedEvent.java": "@org.jmolecules.ddd.annotation.DomainEvent\npublic class PaymentCreatedEvent",
    "src/main/java/com/example/payments/payment/domain/event/PaymentStateChangedEvent.java": "@org.jmolecules.ddd.annotation.DomainEvent\npublic class PaymentStateChangedEvent"
}

prefix = "/home/damian/sandbox/statemachine-payments/"

for filepath, replacement in files_to_update.items():
    full_path = os.path.join(prefix, filepath)
    if os.path.exists(full_path):
        with open(full_path, 'r') as f:
            content = f.read()
        
        target = replacement.split('\n')[1]
        if target in content and replacement not in content:
            content = content.replace(target, replacement)
            with open(full_path, 'w') as f:
                f.write(content)
            print(f"Updated {full_path}")
    else:
        print(f"NOT FOUND {full_path}")

