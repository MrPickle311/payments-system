# Payments Common Module

A shared library containing common domain objects, DTOs, API specifications, and utility classes shared across the `statemachine-payments` ecosystem.

---

## 🧭 Navigation

- 🏠 **[Workspace Root README](../../README.md)**
- 📁 **[StateMachine Payments Root README](../README.md)**

---

## 📦 Core Contents

The module includes:
1. **DTOs**:
   - `DebitRequest` & `DebitResponse`: Models for balance verification and deduction.
   - `LedgerEvent`: Payload schemas for publishing accounting entries to Apache Kafka.
2. **Shared Kernel**:
   - Common utilities, validation rules, and configuration templates used by multiple microservices to prevent code duplication.

---

## 🛠️ How to Include

This library is a dependency in other services' `pom.xml`:

```xml
<dependency>
    <groupId>com.example</groupId>
    <artifactId>payments-common</artifactId>
    <version>${project.version}</version>
</dependency>
```
