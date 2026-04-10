# Distributed Payments Microservices Design

**Date:** 2026-04-10
**Status:** Draft
**Topic:** Transitioning from monolithic payments app to a distributed microservices architecture.

## 1. Overview
The current `statemachine-payments` application is being refactored into a multi-module Maven project consisting of three independent microservices and one shared common library.

### Services:
1.  **Payment Service (Orchestrator):** Manages the state machine and coordinates the payment lifecycle.
2.  **Wallet Service:** Manages user balances and handles debit/credit operations via REST.
3.  **Ledger Service:** Records immutable transaction logs for auditing via Kafka.

## 2. Architecture & Communication
The system follows an **Orchestration** pattern where the Payment Service is the central authority.

- **Payment Service → Wallet Service:** **REST API (Synchronous)**
  - Reason: Immediate consistency is required for balance checks before proceeding with a payment.
- **Payment Service → Ledger Service:** **Kafka (Asynchronous)**
  - Reason: Eventual consistency is sufficient for auditing. This prevents the audit trail from blocking the critical payment path.

## 3. Project Structure (Maven Multi-Module)
```text
statemachine-payments (Root)
├── pom.xml (Parent)
├── payments-common (Module)
│   └── Shared Money class, Enums, and DTOs
├── payment-service (Module)
│   └── Port 8080, DB: payment_db
├── wallet-service (Module)
│   └── Port 8081, DB: wallet_db
└── ledger-service (Module)
    └── Port 8082, DB: ledger_db
```

## 4. Technology Stack
- **Framework:** Spring Boot 3.x, Spring Cloud
- **State Management:** Spring State Machine
- **Persistence:** PostgreSQL (One independent database per service)
- **Messaging:** Apache Kafka
- **Communication:** RestTemplate / WebClient
- **Containerization:** Docker (Layered JARs)
- **Orchestration:** Kubernetes (Deployments, Services, ConfigMaps, Secrets)

## 5. Implementation Details

### Shared Library (`payments-common`)
- **Money:** Core domain object for currency-safe arithmetic.
- **Enums:** `PaymentState`, `PaymentEvent`.
- **DTOs:** `DebitRequest`, `DebitResponse`, `LedgerEvent`.

### Wallet Service
- **Persistence:** PostgreSQL `wallet_db`.
- **Endpoints:** `POST /wallets/debit`.
- **Logic:** Atomic balance check and update.

### Ledger Service
- **Persistence:** PostgreSQL `ledger_db`.
- **Listener:** Kafka consumer on topic `payment-ledger-events`.
- **Logic:** Persists immutable transaction records.

### Kubernetes Deployment
- **Images:** One Dockerfile per service using multi-stage builds.
- **Manifests:**
  - `k8s/payment-service.yaml`
  - `k8s/wallet-service.yaml`
  - `k8s/ledger-service.yaml`
  - `k8s/kafka.yaml` (Optional/External)
  - `k8s/postgres.yaml` (Optional/External)
- **Config:** Using `ConfigMaps` for service URLs and `Secrets` for database credentials.

## 6. Error Handling & Resilience
- **Distributed Transactions:** Not using JTA/XA. Using the State Machine to manage compensating actions if a downstream call fails.
- **Kafka Retries:** Built-in Kafka producer retries for the Ledger Service.
- **REST Timeouts:** Configured timeouts for the Wallet Service to prevent cascading failures.

## 7. Test Strategy
- **Unit Tests:** For each module's business logic.
- **Integration Tests:** Using Testcontainers for PostgreSQL and Kafka.
- **System Tests:** Mocking one service while testing another.
