import os

filepath = "/home/damian/sandbox/statemachine-payments/pom.xml"

with open(filepath, 'r') as f:
    content = f.read()

content = content.replace(
    '''        <dependency>
            <groupId>org.jmolecules</groupId>
            <artifactId>jmolecules-ddd</artifactId>
            <version>1.9.0</version>
        </dependency>''',
    '''        <dependency>
            <groupId>org.jmolecules</groupId>
            <artifactId>jmolecules-ddd</artifactId>
            <version>1.9.0</version>
        </dependency>
        <dependency>
            <groupId>org.jmolecules</groupId>
            <artifactId>jmolecules-events</artifactId>
            <version>1.9.0</version>
        </dependency>'''
)

with open(filepath, 'w') as f:
    f.write(content)

event_files = [
    "src/main/java/com/example/payments/payment/domain/event/PaymentCreatedEvent.java",
    "src/main/java/com/example/payments/payment/domain/event/PaymentStateChangedEvent.java"
]

prefix = "/home/damian/sandbox/statemachine-payments/"
for evt in event_files:
    full_path = os.path.join(prefix, evt)
    print("Fixing", full_path)
    with open(full_path, 'r') as f:
        content = f.read()
    content = content.replace("@org.jmolecules.ddd.annotation.DomainEvent", "@org.jmolecules.event.annotation.DomainEvent")
    with open(full_path, 'w') as f:
        f.write(content)

