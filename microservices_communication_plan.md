## Goal Description
The objective is to further decompose the system into a microservices architecture. We will extract the domain logic for Authorization, FX (Foreign Exchange), Fee Calculation, Fraud Detection, Limits, and Sanctions from `payment-service` into independent microservices. 

Additionally, we will modernize inter-service communication:
1. **Synchronous Communication (gRPC)**: Communication between `payment-service` and the new microservices (`authorization`, `fx`, `fee`, `fraud`, `limits`, `sanctions`), as well as the existing `wallet-service`, will be migrated to gRPC for high-performance and strongly typed RPC calls. We will use the `net.devh:grpc-spring-boot-starter` library.
2. **Asynchronous Communication (Kafka)**: Communication between `payment-service` and `webhooks-service` (for sending outbound webhooks) will be established using Kafka to support resilient, event-driven webhooks processing.

## Proposed Changes

### 1. Protobuf Definitions (`payments-common`)
We will add `protobuf-maven-plugin` and gRPC dependencies to the `payments-common` module to centralize our API contracts. This will allow all microservices to use the generated Java stubs.
#### [NEW] `src/main/proto/wallet.proto`
#### [NEW] `src/main/proto/authorization.proto`
#### [NEW] `src/main/proto/fx.proto`
#### [NEW] `src/main/proto/fee.proto`
#### [NEW] `src/main/proto/fraud.proto`
#### [NEW] `src/main/proto/limits.proto`
#### [NEW] `src/main/proto/sanctions.proto`
#### [MODIFY] `pom.xml`
- Add gRPC dependencies (`io.grpc:grpc-protobuf`, `io.grpc:grpc-stub`).
- Use the existing `os-maven-plugin` and `protobuf-maven-plugin` configuration to generate Java classes during the build.

---

### 2. New Microservices
We will generate new standard Spring Boot modules for the extracted domains. Each will depend on `payments-common` and expose a gRPC server on port `9091` (they will be hidden behind a Kubernetes ingress in production).
#### [NEW] `authorization-service` module
- Expose a gRPC server for `payment-service` to initiate authorization.
- Acts as an internal domain service handling calls to external auth providers. It abstracts the external integration so `payment-service` only sees a simple gRPC request/response.
#### [NEW] `fx-service` module
#### [NEW] `fee-service` module
#### [NEW] `fraud-service` module
#### [NEW] `limits-service` module
#### [NEW] `sanctions-service` module
#### [MODIFY] Root `pom.xml`
- Include the new modules.
- Add `net.devh:grpc-server-spring-boot-starter` and `net.devh:grpc-client-spring-boot-starter` to `<dependencyManagement>`.

---

### 3. Payment Service Updates (`payment-service`)
We will rip out the local dummy implementations and replace them with real gRPC clients and Kafka producers/consumers.
#### [MODIFY] `pom.xml`
Add `grpc-client-spring-boot-starter` and `spring-kafka`.
#### [DELETE] `com.example.payments.fee` package
#### [DELETE] `com.example.payments.fraud` package
#### [DELETE] `PaymentWebhookController.java`
Remove the webhook controller entirely since external webhooks are no longer needed to drive the state machine sub-states.
#### [MODIFY] `PaymentController.java`
Add a new `POST /api/payments/{id}/execute` endpoint to replace the old `INITIATE` webhook trigger. This provides a clear, RESTful way for the client to kick off the parallel saga processing after creating the payment.
#### [MODIFY] `WalletClient.java`
Convert the existing implementation to use a real network call to the `wallet-service` using the generated `WalletServiceGrpc.WalletServiceBlockingStub`.
#### [MODIFY] `PaymentProcessingSaga.java` & internal services
Replace local calls to dummy `AuthorizationService`, `FxService`, `LimitsService`, `SanctionsService` with actual blocking gRPC client calls to the respective internal microservices. Since we are using Virtual Threads, these blocking calls are perfectly efficient. The state machine will handle Auth and Fraud entirely under the hood, automatically transitioning to `COMPLETE` when all gRPC calls return successfully.
#### [MODIFY] `WebhookService.java`
Replace local webhook processing with a `KafkaTemplate` call that publishes a JSON payload to the `payment.webhooks` topic.

---

### 4. Wallet Service Updates (`wallet-service`)
#### [MODIFY] `pom.xml`
Add `grpc-server-spring-boot-starter`.
#### [NEW] `WalletGrpcController.java`
Extend `WalletServiceImplBase` to expose the wallet logic over gRPC.

---

### 5. Webhooks Service Updates (`webhooks-service`)
#### [MODIFY] `pom.xml`
Ensure `spring-kafka` is present.
#### [NEW] `WebhookKafkaListener.java`
Implement a `@KafkaListener` configured to consume from the `payment.webhooks` topic and execute the actual webhook requests.

## Verification Plan
### Automated Tests
- Build the entire multi-module project to ensure `.proto` files compile and stubs are generated properly across all modules.
- Run `mvn clean test` on all modified modules to verify that mocked components (like gRPC stubs and Kafka templates) don't break existing business logic.

### Manual Verification
- We will start a local Kafka instance and run `payment-service` alongside the `webhooks-service` and verify that a webhook message is produced and consumed.
- Start `payment-service` and the new gRPC microservices. Use a gRPC UI tool or execute a full payment flow to ensure the parallel saga successfully communicates with all new microservices over gRPC without errors.
