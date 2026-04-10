# Distributed Payments Refactor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refactor the existing payments monolith into a Maven multi-module microservices architecture consisting of `payments-common`, `payment-service`, `wallet-service`, and `ledger-service`.

**Architecture:** Distributed system using a Synchronous Orchestration pattern. `payment-service` coordinates `wallet-service` (REST) and `ledger-service` (Kafka). Each service has its own PostgreSQL database.

**Tech Stack:** Java 17, Spring Boot 3.2.5, Spring State Machine, PostgreSQL, Apache Kafka, Maven, Kubernetes.

---

### Task 1: Initialize Root Project & Multi-Module Structure

**Files:**
- Create: `pom.xml` (Root)
- Modify: `.gitignore`

- [ ] **Step 1: Create the root `pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.example</groupId>
    <artifactId>statemachine-payments-root</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <packaging>pom</packaging>

    <modules>
        <module>payments-common</module>
        <module>payment-service</module>
        <module>wallet-service</module>
        <module>ledger-service</module>
    </modules>

    <properties>
        <java.version>17</java.version>
        <spring-boot.version>3.2.5</spring-boot.version>
        <spring-cloud.version>2023.0.1</spring-cloud.version>
        <lombok.version>1.18.30</lombok.version>
    </properties>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-dependencies</artifactId>
                <version>${spring-boot.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>
</project>
```

- [ ] **Step 2: Initialize directory structure for modules**

Run: `mkdir -p payments-common/src/main/java/com/example/payments/common payment-service/src/main/java/com/example/payments/payment wallet-service/src/main/java/com/example/payments/wallet ledger-service/src/main/java/com/example/payments/ledger`

- [ ] **Step 3: Commit structural changes**

```bash
git add pom.xml
git commit -m "chore: initialize multi-module maven structure"
```

---

### Task 2: Create `payments-common` Module

**Files:**
- Create: `payments-common/pom.xml`
- Create: `payments-common/src/main/java/com/example/payments/common/domain/Money.java`
- Create: `payments-common/src/main/java/com/example/payments/common/domain/enums/PaymentState.java`
- Create: `payments-common/src/main/java/com/example/payments/common/domain/enums/PaymentEvent.java`

- [ ] **Step 1: Create `payments-common/pom.xml`**

```xml
<project ...>
    <parent>
        <groupId>com.example</groupId>
        <artifactId>statemachine-payments-root</artifactId>
        <version>0.0.1-SNAPSHOT</version>
    </parent>
    <artifactId>payments-common</artifactId>
    <dependencies>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
        <dependency>
            <groupId>jakarta.validation</groupId>
            <artifactId>jakarta.validation-api</artifactId>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 2: Move `Money`, `PaymentState`, and `PaymentEvent` from root to `payments-common`**

(Move and update package names to `com.example.payments.common...`)

- [ ] **Step 3: Run `mvn clean install` in `payments-common`**

Run: `mvn clean install -pl payments-common`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit `payments-common`**

```bash
git add payments-common
git commit -m "feat: setup payments-common shared library"
```

---

### Task 3: Migrate `payment-service`

**Files:**
- Create: `payment-service/pom.xml`
- Move: existing `src/main/java/com/example/payments/payment/...` and `src/main/resources` to `payment-service`

- [ ] **Step 1: Create `payment-service/pom.xml`**

(Include `spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `spring-statemachine-starter`, `payments-common` dependency)

- [ ] **Step 2: Refactor imports in `payment-service` to use `payments-common`**

- [ ] **Step 3: Update `payment-service/src/main/resources/application.yml`**

```yaml
spring:
  datasource:
    url: jdbc:postgresql://payment-db:5432/payment_db
```

- [ ] **Step 4: Verify build**

Run: `mvn clean compile -pl payment-service`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit `payment-service` migration**

```bash
git add payment-service
git commit -m "refactor: migrate payment-service to its own module"
```

---

### Task 4: Implement `wallet-service`

**Files:**
- Create: `wallet-service/pom.xml`
- Create: `wallet-service/src/main/java/com/example/payments/wallet/domain/WalletAccount.java`
- Create: `wallet-service/src/main/java/com/example/payments/wallet/api/WalletController.java`

- [ ] **Step 1: Create `wallet-service/pom.xml`**

(Include `spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `postgresql`, `payments-common`)

- [ ] **Step 2: Implement `WalletController` with `POST /wallets/debit`**

```java
@RestController
@RequestMapping("/wallets")
@RequiredArgsConstructor
public class WalletController {
    private final WalletService walletService;

    @PostMapping("/debit")
    public ResponseEntity<DebitResponse> debit(@RequestBody DebitRequest request) {
        return ResponseEntity.ok(walletService.debit(request));
    }
}
```

- [ ] **Step 3: Implement `WalletService` logic (balance check and debit)**

- [ ] **Step 4: Commit `wallet-service`**

```bash
git add wallet-service
git commit -m "feat: implement distributed wallet-service"
```

---

### Task 5: Implement `ledger-service`

**Files:**
- Create: `ledger-service/pom.xml`
- Create: `ledger-service/src/main/java/com/example/payments/ledger/infrastructure/KafkaConsumer.java`
- Create: `ledger-service/src/main/java/com/example/payments/ledger/domain/LedgerEntry.java`

- [ ] **Step 1: Create `ledger-service/pom.xml`**

(Include `spring-boot-starter-data-jpa`, `spring-kafka`, `postgresql`, `payments-common`)

- [ ] **Step 2: Implement `KafkaConsumer` to listen to `payment-ledger-events` topic**

- [ ] **Step 3: Implement `LedgerService` to persist entries to `ledger_db`**

- [ ] **Step 4: Commit `ledger-service`**

```bash
git add ledger-service
git commit -m "feat: implement distributed ledger-service"
```

---

### Task 6: Kubernetes Deployment Manifests

**Files:**
- Create: `k8s/payment-service.yaml`
- Create: `k8s/wallet-service.yaml`
- Create: `k8s/ledger-service.yaml`
- Create: `k8s/infrastructure.yaml` (Postgres and Kafka)

- [ ] **Step 1: Create `k8s/infrastructure.yaml` for Postgres (3 DBs) and Kafka**

- [ ] **Step 2: Create Deployments and Services for all three microservices**

- [ ] **Step 3: Commit K8s manifests**

```bash
git add k8s
git commit -m "deploy: add kubernetes manifests for all services"
```

---

### Task 7: Final Integration & E2E Verification

- [ ] **Step 1: Update `payment-service` to call `wallet-service:8081` via REST**
- [ ] **Step 2: Update `payment-service` to publish to Kafka broker in K8s**
- [ ] **Step 3: Run full build and verify all tests**

Run: `mvn clean install`
Expected: BUILD SUCCESS across all modules.
