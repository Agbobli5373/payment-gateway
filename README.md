# Mini Payment Gateway

[![CI](https://github.com/Agbobli5373/payment-gateway/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/Agbobli5373/payment-gateway/actions/workflows/ci.yml)

Spring Boot payment gateway with JWT/RBAC, idempotency, reconciliation, security controls, and observability.

## Run locally (Docker Compose)

From `payment-gateway/`:

```bash
docker compose up --build
```

App endpoints are available via nginx:
- `https://localhost` (TLS terminated by nginx)
- Swagger UI: `https://localhost/swagger-ui.html`

## Environment variables

Key variables (with defaults in `application.yml`):
- `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`
- `JWT_SECRET`, `JWT_EXPIRY_SECONDS`
- `RATE_LIMIT_RPM`
- `RECONCILE_CRON`

## Postman

Artifacts:
- Collection: `postman/Mini Payment Gateway.postman_collection.json`
- Environment: `postman/Local Docker.postman_environment.json`

## Docker image tags (GHCR)

CI publishes to `ghcr.io/Agbobli5373/payment-gateway/`:
- `:<commit-sha>` on `main` and on all tagged releases
- `:latest` on `main`
- `:<vX.Y.Z>` (plus `:<commit-sha>`) on tag pushes matching `v*` (e.g. `v1.2.3`)

### Import and run

1. Import both files into Postman.
2. Select environment **Local Docker**.
3. Run `Auth -> Get token (admin)` and copy `token` from response.
4. Set environment variable `token` to that value.
5. Run endpoints in order:
   - Accounts: create and check balance
   - Payments: initiate, process, settle, reverse/fail
   - Reconciliation: run and fetch reports

Mutating endpoints require header `X-Idempotency-Key` (UUID). The provided requests auto-generate it using Postman dynamic variables.

## Story traceability (Epic 9/10)

- Epic 9.1: OpenAPI annotations + common response components in `payment-gateway/src/main/java/com/minipaygateway/config/OpenApiConfig.java` and controllers.
- Epic 9.2: Postman collection/environment in `postman/`.
- Epic 10: quality gates and tests in `payment-gateway/pom.xml`, `.github/workflows/ci.yml`, and `payment-gateway/src/test/java/com/minipaygateway/`.

### Acceptance criteria mapping

- **US-9.1 (OpenAPI completeness)**  
  - OpenAPI availability + core path/component checks: `Epic9OpenApiIntegrationTest`.
  - Endpoint operation/response metadata: `AuthController`, `AccountController`, `PaymentController`, `ReconcileController`.
- **US-9.2 (Postman deliverables)**  
  - Collection: `postman/Mini Payment Gateway.postman_collection.json`  
  - Environment: `postman/Local Docker.postman_environment.json`
- **US-10.1 (coverage gate + unit tests)**  
  - JaCoCo verify gate in `payment-gateway/pom.xml`.  
  - Unit coverage examples: `JwtAuthFilterTest`, `RateLimitFilterTest`.
- **US-10.2 (integration with Testcontainers + CI)**  
  - Integration suites: `Epic4SecurityIntegrationTest`, `Epic5PaymentIntegrationTest`, `Epic6ReconciliationIntegrationTest`, `Epic7And8SecurityObservabilityIntegrationTest`, `Epic9OpenApiIntegrationTest`, `Epic10ContractsIntegrationTest`.  
  - CI: `.github/workflows/ci.yml` running `mvn verify`.
- **US-10.3 (idempotency contracts)**  
  - Same key + same body replay and conflict scenarios: `Epic5PaymentIntegrationTest`, `Epic10ContractsIntegrationTest`, `AccountApiIntegrationTest`.
- **US-10.4 (security matrix)**  
  - Missing JWT 401 / wrong role 403 / missing idempotency key 400 / rate-limit 429 covered in `Epic4SecurityIntegrationTest`, `PaymentIdempotencyHeaderIntegrationTest`, and `Epic7And8SecurityObservabilityIntegrationTest`.
