# Wallet Service

The Wallet Service is responsible for managing customer accounts, checking balances, and performing safe debit operations during the payment reservation phase.

---

## 🧭 Navigation

- 🏠 **[Workspace Root README](file:///home/damian/sandbox/README.md)**
- 📁 **[StateMachine Payments Root README](file:///home/damian/sandbox/statemachine-payments/README.md)**

---

## 🏗️ Technical Highlights

- **Balance Protection**: Employs optimistic locking to prevent concurrent balance corruption or double-spending.
- **Hexagonal Boundaries**:
  - `WalletAccount` (Domain Entity)
  - `WalletService` (Application logic)
  - `WalletController` (Adapter exposing REST interface)
  - `WalletAccountRepositoryAdapter` (Persistence mapper)

---

## 🚀 Key APIs

### REST Endpoints
- `POST /api/wallets/debit`: Atomically debit funds from a user's wallet.
- `GET /api/wallets/{accountId}`: Fetch balance details.
