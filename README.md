# Mini Payment Gateway

[![CI](https://github.com/Agbobli5373/payment-gateway/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/Agbobli5373/payment-gateway/actions/workflows/ci.yml)

Mini Payment Gateway is a portfolio-grade fintech backend built with Java 21, Spring Boot 3.5, and PostgreSQL 15. It models the hard parts of payment infrastructure instead of stopping at CRUD: immutable double-entry ledger posting, idempotent commands, JWT/RBAC-protected APIs, payment state transitions, reconciliation, rate limiting, auditability, and container-ready delivery.

The project is API-first. Its primary interfaces are REST endpoints, Swagger/OpenAPI, Postman collections, and operational endpoints. The goal is to demonstrate production-minded backend engineering for a payment platform, not live scheme integration or a full merchant-facing frontend.

## Why This Project Exists

This system was designed as a realistic backend exercise for a modern payment gateway. It focuses on the failure modes that matter in fintech systems:

- duplicate requests and double-charge prevention
- invalid state transitions across a payment lifecycle
- ledger correctness and atomic money movement
- access control across operational roles
- discrepancy detection through reconciliation
- operational resilience through health checks, observability, and CI quality gates

## Core Capabilities

- Account management with typed accounts such as `MERCHANT`, `FLOAT`, `FEE_POOL`, and `SUSPENSE`
- Double-entry ledger posting where balances are derived from ledger entries rather than a mutable balance column
- Payment lifecycle commands for initiate, process, settle, reverse, and fail flows
- Idempotency protection for mutating account and payment requests using `X-Idempotency-Key`
- JWT authentication with role-based access control for `ADMIN`, `MERCHANT`, and `AUDITOR`
- Manual and scheduled reconciliation with persisted reports and discrepancy classification
- Security hardening with rate limiting, correlation IDs, structured error responses, and masked sensitive values in logs
- OpenAPI 3 documentation, Swagger UI, Postman artifacts, Docker packaging, and GitHub Actions CI/CD

## Technology Stack

- Java 21
- Spring Boot 3.5.13
- Spring Web, Validation, Security, Actuator, Data JPA
- PostgreSQL 15
- Flyway for schema migrations
- Springdoc OpenAPI for API documentation
- Bucket4j for rate limiting
- JJWT for token handling
- JUnit 5, Spring Boot Test, Spring Security Test, and Testcontainers for verification
- JaCoCo and OWASP Dependency-Check for quality and supply-chain controls

## Architecture Summary

- Style: modular monolith with clear separation between `controller`, `service`, `repository`, and persistence layers
- Transaction model: payment status changes and ledger effects are handled in services with explicit transactional boundaries
- Persistence model: PostgreSQL is the system of record, with versioned Flyway migrations under `src/main/resources/db/migration`
- Security model: stateless JWT auth, RBAC, rate limiting, and request filters for correlation and idempotency enforcement
- Operations model: health and info endpoints, Docker health checks, structured logging, and CI build/test/security stages

## Financial Safety Rules

These are the main invariants the codebase is built around:

- The ledger is the source of truth for balances.
- Ledger entries are append-only in business terms; corrections are represented through compensating movements.
- Merchant debits must not drive balances below allowed limits.
- Payment state transitions are explicit and validated.
- Idempotency records are stored so retries can be replayed safely or rejected on conflict.
- Reconciliation exists to detect when operational state and ledger state drift apart.

## API Overview

Public authentication endpoint:

- `POST /api/v1/auth/token`

Account endpoints:

- `POST /api/v1/accounts` for `ADMIN`
- `GET /api/v1/accounts/{id}/balance` for `ADMIN` and `MERCHANT`

Payment endpoints:

- `POST /api/v1/payments/initiate` for `ADMIN` and `MERCHANT`
- `GET /api/v1/payments` for `ADMIN` and `MERCHANT`
- `GET /api/v1/payments/{ref}` for `ADMIN` and `MERCHANT`
- `POST /api/v1/payments/{ref}/process` for `ADMIN`
- `POST /api/v1/payments/{ref}/settle` for `ADMIN`
- `POST /api/v1/payments/{ref}/reverse` for `ADMIN`
- `POST /api/v1/payments/{ref}/fail` for `ADMIN`

Reconciliation endpoints:

- `POST /api/v1/reconcile` for `ADMIN` and `AUDITOR`
- `GET /api/v1/reconcile/reports` for `ADMIN` and `AUDITOR`
- `GET /api/v1/reconcile/reports/{id}` for `ADMIN` and `AUDITOR`

Operational and documentation endpoints:

- `GET /actuator/health`
- `GET /actuator/info`
- `GET /v3/api-docs`
- `GET /swagger-ui.html`

## Local Development

### Prerequisites

- Java 21
- Docker and Docker Compose
- Maven or the included wrapper `./mvnw`

### Option 1: Run Spring Boot directly

From the `payment-gateway/` directory:

```bash
./mvnw spring-boot:run
```

When running this way, Spring Boot can automatically start PostgreSQL for local development using `compose.yaml`. The API will be available on `http://localhost:8080`.

Useful note:

- If you already have PostgreSQL running locally, disable automatic Compose support with `spring.docker.compose.enabled=false`.

### Option 2: Run the full container stack with nginx and TLS

This option runs `nginx -> app -> postgres` using `docker-compose.yml`.

1. Generate the local development certificate once by following `docker/nginx/README.md`.
2. Start the stack:

```bash
docker compose up --build
```

3. Open:

- `https://localhost`
- `https://localhost/swagger-ui.html`

To stop and remove containers:

```bash
docker compose down
```

To also remove the Postgres volume:

```bash
docker compose down -v
```

## Local Demo Users

Default development users are defined in `src/main/resources/application.yml`:

- `admin` / `admin` with role `ADMIN`
- `merchant` / `merchant` with role `MERCHANT`
- `merchant-other` / `merchant` with role `MERCHANT`
- `auditor` / `auditor` with role `AUDITOR`

These credentials are for local development only. Replace them and use properly encoded secrets in any real deployment.

## Idempotency

Mutating account and payment requests require the `X-Idempotency-Key` header. The expected value is a UUID. The service stores request fingerprints so safe retries can replay the original response, while mismatched replays fail with a conflict response.

This is especially important for network retries, API client timeouts, and any workflow where a client cannot be sure whether a previous request already succeeded.

## Configuration

Key environment variables are externalized in `src/main/resources/application.yml`:

- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`
- `JWT_SECRET`
- `JWT_EXPIRY_SECONDS`
- `RATE_LIMIT_RPM`
- `RECONCILE_CRON`
- `RECONCILE_CURRENCIES`
- `IDEMPOTENCY_CLEANUP_CRON`

## Repository Layout

```text
.
├── .github/workflows/ci.yml
├── compose.yaml
├── docker-compose.yml
├── docker/
├── postman/
├── src/main/java/com/minipaygateway/
├── src/main/resources/
├── src/test/java/com/minipaygateway/
└── pom.xml
```

Important directories and files:

- `src/main/java/com/minipaygateway/controller` contains the REST API layer
- `src/main/java/com/minipaygateway/service` contains business rules and transaction orchestration
- `src/main/java/com/minipaygateway/domain` contains JPA entities and enums
- `src/main/java/com/minipaygateway/filter` contains auth, rate-limit, idempotency, and correlation filters
- `src/main/resources/db/migration` contains Flyway migrations
- `src/test/java/com/minipaygateway` contains unit, contract, concurrency, and integration tests
- `postman/` contains importable API collections for local execution
- `docker/` contains the application image definition and nginx TLS configuration

## Build, Test, and Quality Gates

Package the application:

```bash
./mvnw clean package
```

Run the full verification suite:

```bash
./mvnw verify
```

Run dependency vulnerability checks:

```bash
./mvnw -DskipTests dependency-check:check
```

The project enforces:

- JaCoCo coverage checks during `verify`
- integration tests backed by Testcontainers
- Spring Security and idempotency contract verification
- OWASP Dependency-Check with fail-on-severity behavior in CI

Representative tests include:

- `AccountApiIntegrationTest`
- `Epic4SecurityIntegrationTest`
- `Epic5PaymentIntegrationTest`
- `Epic6ReconciliationIntegrationTest`
- `Epic7And8SecurityObservabilityIntegrationTest`
- `Epic9OpenApiIntegrationTest`
- `Epic10ContractsIntegrationTest`
- `LedgerServiceConcurrentDebitTest`
- `JwtAuthFilterTest`
- `RateLimitFilterTest`

## Documentation and API Artifacts

- Swagger UI: `http://localhost:8080/swagger-ui.html` or `https://localhost/swagger-ui.html` behind nginx
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- Postman collection: `postman/Mini Payment Gateway.postman_collection.json`
- Postman environment: `postman/Local Docker.postman_environment.json`

The Postman collection already includes request flows for authentication, accounts, payments, and reconciliation. For idempotent requests, it uses Postman dynamic variables to generate `X-Idempotency-Key` values automatically.

## CI/CD and Images

GitHub Actions runs the pipeline in `.github/workflows/ci.yml`:

- build
- test and `mvn verify`
- dependency vulnerability scan
- Docker image publishing to GHCR

Published image tags follow this pattern:

- `:latest` on `main`
- `:<commit-sha>` on `main` and tagged releases
- `:<vX.Y.Z>` on semantic version tags such as `v1.2.3`

## Scope Coverage

The implementation covers the full journey from platform setup to delivery-focused hardening:

- foundation and data layer
- accounts and double-entry ledger
- idempotency
- JWT authentication and RBAC
- payment lifecycle state machine
- reconciliation engine
- security controls and rate limiting
- observability and audit logging
- OpenAPI and Postman documentation
- automated tests, coverage gates, and CI/CD publishing

## Non-Goals

This project intentionally does not attempt to be a complete commercial gateway. It does not include:

- live card-scheme or mobile-money integrations
- real PCI certification
- a foreign exchange engine
- a production merchant dashboard UI

## Summary

If you want to evaluate the project quickly, start with:

1. `./mvnw verify`
2. `docker compose up --build`
3. `https://localhost/swagger-ui.html`
4. the Postman collection in `postman/`

That gives the fastest view of the system's API design, transaction rules, security posture, and operational readiness.
