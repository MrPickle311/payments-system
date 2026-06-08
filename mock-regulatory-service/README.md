# Mock Regulatory Service

The **Mock Regulatory Service** simulates a sluggish external regulatory auditing API. It is primarily used to validate the reliability, performance, and scaling characteristics (e.g., partitioning) of the **Export Batch Service**.

---

## 🧭 Navigation

- 🏠 **[Workspace Root README](../../README.md)**
- 📁 **[StateMachine Payments Root README](../README.md)**

---

## ⚙️ Configuration & Behavior

- **Artificial Latency**: Configurable API call delays to simulate high load, slow response, or network failures.
- **REST Endpoints**:
  - `POST /api/regulatory/report`: Endpoint accepting bulk list of payments to process.
  - Returns `200 OK` on successful validation.
- **Purpose in Scaling Tests**: Responding slowly triggers the partitioning queue in the `export-batch-service`, allowing developers to verify concurrent scaling, worker balancing, and transaction retry policies under load.
