# Payment Service

The core payment orchestration engine of the ecosystem. It manages the transactional lifecycle of payment requests, coordinates fraud checks, updates wallets, and publishes settlement events.

---

## 🧭 Navigation

- 🏠 **[Workspace Root README](../../README.md)**
- 📁 **[StateMachine Payments Root README](../README.md)**

---

## 🏗️ Architecture & Features

This service is structured around **Hexagonal Architecture** principles and includes:

### 🧩 Domain Domain State Machine
The payment flow transitions through distinct states managed by **Spring State Machine**:
- **States**: `INITIATED`, `FRAUD_CHECK_PENDING`, `RESERVATION_PENDING`, `COMPLETED`, `FAILED`.
- **Events**: `CREATE`, `FRAUD_APPROVED`, `FRAUD_REJECTED`, `RESERVE_SUCCESS`, `RESERVE_FAIL`, `AUTHORIZE`.

### 🔄 Main Pipelines
1. **Initiate Payment**: Persists a new `Payment` in the `INITIATED` state.
2. **Fraud Check**: Assesses risk levels and persists a `FraudRecord`.
3. **Wallet Balance Check**: Interacts with the external **Wallet Service** to lock the amount.
4. **Completion & Event Publication**: Upon transition to `COMPLETED`, it triggers a Kafka message to notify the Ledger and Export engines.

---

## 🚀 Key APIs

### REST Endpoints
- `POST /api/payments`: Create a payment session.
- `GET /api/payments/{id}`: Fetch detailed state history.
- `POST /api/payments/webhook`: Webhook handler for external gateway integrations.

All endpoints expose OpenAPI specifications at `/swagger-ui.html`.
