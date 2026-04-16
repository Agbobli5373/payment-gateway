# API Manual Testing Guide

This guide explains how to manually test the Mini Payment Gateway API from end to end using realistic sample data, expected statuses, and expected response behavior.

It is based on the live controller contracts, validation rules, security filters, and integration tests in the project.

## 1. Setup And Preconditions

### Base URLs

- Direct Spring Boot app: `http://localhost:8080`
- Full local stack through nginx/TLS: `https://localhost`

### Useful endpoints

- Swagger UI: `/swagger-ui.html`
- OpenAPI JSON: `/v3/api-docs`
- Health: `/actuator/health`
- Info: `/actuator/info`

### Default local users

These users come from `payment-gateway/src/main/resources/application.yml`.

| Role | Username | Password | Notes |
| --- | --- | --- | --- |
| `ADMIN` | `admin` | `admin` | Can create accounts and execute payment state changes |
| `MERCHANT` | `merchant` | `merchant` | Scoped to `ownerRef = merchant-acme` |
| `MERCHANT` | `merchant-other` | `merchant` | Scoped to `ownerRef = other-owner` |
| `AUDITOR` | `auditor` | `auditor` | Can run and read reconciliation reports |

### Required headers

Use these headers unless the endpoint is public:

```text
Authorization: Bearer <jwt>
Content-Type: application/json
```

For authenticated `POST` requests under:

- `/api/v1/accounts`
- `/api/v1/payments/**`

you must also send:

```text
X-Idempotency-Key: <fresh UUID>
```

Notes:

- `POST /api/v1/auth/token` does not need `Authorization`.
- `POST /api/v1/reconcile` does not require `X-Idempotency-Key`.
- `X-Trace-Id` is optional; the server may generate and echo it on the response.

### Important behavior rules before testing

1. Use a fresh UUID for each new mutating request unless you are intentionally testing idempotency replay behavior.
2. Reusing the same idempotency key with a different request body should return `409 IDEMPOTENCY_CONFLICT`.
3. Merchant access is owner-scoped. A merchant can only see balances and payments that belong to its configured owner context.
4. Payment lifecycle order matters: `initiate -> process -> settle -> reverse` or `initiate -> fail`.
5. Reconciliation windows must cover the payment event timestamps and use the matching currency.

## 2. Sample Data And Placeholders

Use these values across the guide:

### Placeholders

- `{{adminToken}}`
- `{{merchantToken}}`
- `{{auditorToken}}`
- `{{payerAccountId}}`
- `{{payeeAccountId}}`
- `{{paymentRef}}`
- `{{reportId}}`
- `{{uuid}}` for a fresh idempotency key

### Recommended happy-path values

- Currency: `USD`
- Payer ownerRef: `merchant-acme`
- Payee ownerRef: `other-owner`
- Payer opening balance: `200.00`
- Payee opening balance: `0.00`
- Happy-path payment amount: `25.0000`
- Low-balance negative test amount: `250.00`

### Example reconcile window

Adjust the timestamps to cover your current test run:

```json
{
  "from": "2026-04-16T10:00:00Z",
  "to": "2026-04-16T12:00:00Z",
  "currency": "USD"
}
```

Reusable payload fixtures are also stored in:

- `payment-gateway/src/test/resources/test-data/manual-e2e-flow-test-data.json`

## 3. Response Types You Will See

### `CreateAccountResponse`

Key fields:

- `id`
- `ownerRef`
- `currency`
- `accountType`

### `AccountBalanceResponse`

Key fields:

- `accountId`
- `currency`
- `balance`
- `asOf`

### `PaymentTransactionResponse`

Key fields:

- `reference`
- `status`
- `payerAccountId`
- `payeeAccountId`
- `amount`
- `currency`
- `createdAt`
- `updatedAt`
- `settledAt`
- `failedAt`
- `reversedAt`
- `ledgerEntryIds`

Typical status progression:

- `PENDING`
- `PROCESSING`
- `SETTLED`
- `FAILED`
- `REVERSED`

### `ReconciliationReportDetailResponse`

Key fields:

- `id`
- `reconcileFrom`
- `reconcileTo`
- `currency`
- `discrepancyCount`
- `status`
- `expectedCount`
- `expectedSum`
- `actualCount`
- `actualSum`
- `discrepancies`

### Discrepancy types

- `AMOUNT_MISMATCH`
- `MISSING_LEDGER_ENTRY`
- `ORPHANED_LEDGER_ENTRY`

## 4. Authentication Flow

### Step 1: Get admin token

`POST /api/v1/auth/token`

Who calls it:

- Public

Body:

```json
{
  "username": "admin",
  "password": "admin"
}
```

Expected behavior:

- HTTP `200`
- Response contains `token`, `expiresIn`, and `roles`
- Save `token` as `{{adminToken}}`

### Step 2: Get merchant token

`POST /api/v1/auth/token`

Body:

```json
{
  "username": "merchant",
  "password": "merchant"
}
```

Expected behavior:

- HTTP `200`
- Save `token` as `{{merchantToken}}`

### Step 3: Get auditor token

`POST /api/v1/auth/token`

Body:

```json
{
  "username": "auditor",
  "password": "auditor"
}
```

Expected behavior:

- HTTP `200`
- Save `token` as `{{auditorToken}}`

## 5. Core Happy Paths

### Flow A: Settle And Reconcile With Zero Discrepancies

#### Step 1: Create payer account

`POST /api/v1/accounts`

Who calls it:

- `ADMIN`

Headers:

```text
Authorization: Bearer {{adminToken}}
Content-Type: application/json
X-Idempotency-Key: {{uuid}}
```

Body:

```json
{
  "ownerRef": "merchant-acme",
  "currency": "USD",
  "accountType": "MERCHANT",
  "initialBalance": 200.00
}
```

Expected behavior:

- HTTP `201`
- Save response `id` as `{{payerAccountId}}`

#### Step 2: Create payee account

`POST /api/v1/accounts`

Headers:

```text
Authorization: Bearer {{adminToken}}
Content-Type: application/json
X-Idempotency-Key: {{uuid}}
```

Body:

```json
{
  "ownerRef": "other-owner",
  "currency": "USD",
  "accountType": "MERCHANT",
  "initialBalance": 0.00
}
```

Expected behavior:

- HTTP `201`
- Save response `id` as `{{payeeAccountId}}`

#### Step 3: Initiate payment

`POST /api/v1/payments/initiate`

Who calls it:

- `MERCHANT` or `ADMIN`

Headers:

```text
Authorization: Bearer {{merchantToken}}
Content-Type: application/json
X-Idempotency-Key: {{uuid}}
```

Body:

```json
{
  "payerAccountId": {{payerAccountId}},
  "payeeAccountId": {{payeeAccountId}},
  "amount": 25.0000,
  "currency": "USD"
}
```

Expected behavior:

- HTTP `201`
- Response `status` is `PENDING`
- Save response `reference` as `{{paymentRef}}`
- `ledgerEntryIds` is typically empty at this stage

#### Step 4: Process payment

`POST /api/v1/payments/{{paymentRef}}/process`

Who calls it:

- `ADMIN`

Headers:

```text
Authorization: Bearer {{adminToken}}
Content-Type: application/json
X-Idempotency-Key: {{uuid}}
```

Body:

```json
{}
```

Expected behavior:

- HTTP `200`
- Payment is moved into processing state

#### Step 5: Settle payment

`POST /api/v1/payments/{{paymentRef}}/settle`

Headers:

```text
Authorization: Bearer {{adminToken}}
Content-Type: application/json
X-Idempotency-Key: {{uuid}}
```

Body:

```json
{}
```

Expected behavior:

- HTTP `200`
- Payment becomes `SETTLED`
- Settlement timestamps and ledger linkage should be present in the response

#### Step 6: Run reconciliation

`POST /api/v1/reconcile`

Who calls it:

- `ADMIN` or `AUDITOR`

Headers:

```text
Authorization: Bearer {{adminToken}}
Content-Type: application/json
```

Body:

```json
{
  "from": "2026-04-16T10:00:00Z",
  "to": "2026-04-16T12:00:00Z",
  "currency": "USD"
}
```

Expected behavior:

- HTTP `200`
- `discrepancyCount` is `0`
- `status` is `SUCCESS`
- Save `id` as `{{reportId}}`

#### Step 7: Fetch report detail

`GET /api/v1/reconcile/reports/{{reportId}}`

Who calls it:

- `ADMIN` or `AUDITOR`

Headers:

```text
Authorization: Bearer {{adminToken}}
```

Expected behavior:

- HTTP `200`
- `discrepancies` is an empty array

### Flow B: Reverse After Settlement And Reconcile

Repeat Flow A through settlement, then continue with these steps.

#### Step 1: Reverse payment

`POST /api/v1/payments/{{paymentRef}}/reverse`

Who calls it:

- `ADMIN`

Headers:

```text
Authorization: Bearer {{adminToken}}
Content-Type: application/json
X-Idempotency-Key: {{uuid}}
```

Body:

```json
{}
```

Expected behavior:

- HTTP `200`
- Payment becomes `REVERSED`

#### Step 2: Fetch payment detail

`GET /api/v1/payments/{{paymentRef}}`

Who calls it:

- `MERCHANT` or `ADMIN`

Headers:

```text
Authorization: Bearer {{merchantToken}}
```

Expected behavior:

- HTTP `200`
- `status` is `REVERSED`
- `ledgerEntryIds` contains `3` entries

#### Step 3: Run reconciliation again

`POST /api/v1/reconcile`

Headers:

```text
Authorization: Bearer {{adminToken}}
Content-Type: application/json
```

Body:

```json
{
  "from": "2026-04-16T10:00:00Z",
  "to": "2026-04-16T12:00:00Z",
  "currency": "USD"
}
```

Expected behavior:

- HTTP `200`
- `discrepancyCount` is `0`
- `status` is `SUCCESS`

### Flow C: Fail From Pending

#### Step 1: Initiate payment

Use the same initiate request as Flow A.

Expected behavior:

- HTTP `201`
- `status` is `PENDING`

#### Step 2: Fail payment

`POST /api/v1/payments/{{paymentRef}}/fail`

Who calls it:

- `ADMIN`

Headers:

```text
Authorization: Bearer {{adminToken}}
Content-Type: application/json
X-Idempotency-Key: {{uuid}}
```

Body:

```json
{}
```

Expected behavior:

- HTTP `200`
- Payment becomes `FAILED`

#### Step 3: Fetch failed payment

`GET /api/v1/payments/{{paymentRef}}`

Headers:

```text
Authorization: Bearer {{merchantToken}}
```

Expected behavior:

- HTTP `200`
- `status` is `FAILED`
- No settlement or reversal timestamps should be present

## 6. Listing And Visibility Checks

### List payments

`GET /api/v1/payments`

Optional query parameters:

- `status`
- `from`
- `to`
- `payerAccountId`
- `page`
- `size`

Example:

```text
GET /api/v1/payments?status=PENDING&page=0&size=20
```

Expected behavior:

- `MERCHANT` sees only payments within its ownership scope
- `ADMIN` sees all payments
- HTTP `200`

### Get account balance

`GET /api/v1/accounts/{{payerAccountId}}/balance`

Expected behavior:

- `ADMIN` can read any account
- `MERCHANT` can only read accounts belonging to its `ownerRef`
- Response includes `balance` and `asOf`

### List reconciliation reports

`GET /api/v1/reconcile/reports?page=0&size=20`

Expected behavior:

- `ADMIN` and `AUDITOR` can read reports
- HTTP `200`

## 7. Negative And Security Cases

### Missing JWT

Example:

`GET /api/v1/payments/{{paymentRef}}` without `Authorization`

Expected behavior:

- HTTP `401`
- `code = UNAUTHORIZED`
- Detail indicates authentication is required

### Wrong credentials

`POST /api/v1/auth/token`

Body:

```json
{
  "username": "admin",
  "password": "wrong"
}
```

Expected behavior:

- HTTP `401`
- `code = UNAUTHORIZED`
- Detail indicates invalid credentials

### Wrong role

Example:

`POST /api/v1/payments/{{paymentRef}}/process` as merchant

Expected behavior:

- HTTP `403`
- `code = FORBIDDEN`

### Missing idempotency key

Example:

`POST /api/v1/accounts` without `X-Idempotency-Key`

Expected behavior:

- HTTP `400`
- `code = MISSING_IDEMPOTENCY_KEY`

### Invalid idempotency key

Example:

Send `X-Idempotency-Key: not-a-uuid`

Expected behavior:

- HTTP `400`
- `code = MISSING_IDEMPOTENCY_KEY`

### Idempotency conflict

How to test:

1. Send `POST /api/v1/accounts` with one UUID and a valid body.
2. Reuse the same UUID with a different request body.

Expected behavior:

- HTTP `409`
- `code = IDEMPOTENCY_CONFLICT`

### Insufficient balance

Use this initiate request after creating a payer account with only `200.00` balance.

```json
{
  "payerAccountId": {{payerAccountId}},
  "payeeAccountId": {{payeeAccountId}},
  "amount": 250.00,
  "currency": "USD"
}
```

Expected behavior:

- HTTP `422`
- `code = INSUFFICIENT_BALANCE`

### Invalid state transition

How to test:

1. Initiate a payment.
2. Call `POST /api/v1/payments/{{paymentRef}}/settle` before `process`.

Expected behavior:

- HTTP `422`
- `code = INVALID_STATE_TRANSITION`

### Unknown account

`GET /api/v1/accounts/999999999/balance`

Expected behavior:

- HTTP `404`
- `code = ACCOUNT_NOT_FOUND`

### Unknown payment

`GET /api/v1/payments/not-a-real-reference`

Expected behavior:

- HTTP `404`
- `code = TRANSACTION_NOT_FOUND`

### Unknown reconciliation report

`GET /api/v1/reconcile/reports/999999999`

Expected behavior:

- HTTP `404`
- `code = RECONCILIATION_REPORT_NOT_FOUND`

### Rate limit exceeded

This applies only to `/api/v1/payments*`.

How to test:

1. Lower the payment rate limit for your environment if needed.
2. Call the same payment endpoint repeatedly in a short interval.

Expected behavior:

- HTTP `429`
- `code = RATE_LIMIT_EXCEEDED`
- `Retry-After` header is returned

### Database unavailable

How to test:

1. Stop PostgreSQL or break DB connectivity.
2. Call an endpoint that needs the database.

Expected behavior:

- HTTP `503`
- `code = SERVICE_UNAVAILABLE`
- `Retry-After: 5`

## 8. Reconciliation-Specific Notes

### Happy path

For normal payment flows with matching ledger activity:

- `discrepancyCount` should be `0`
- `status` should be `SUCCESS`

### Discrepancy path

If ledger data is intentionally changed or inconsistent, the reconciliation response can still be HTTP `200`, but:

- `discrepancyCount` becomes greater than `0`
- `status` becomes `COMPLETED_WITH_DISCREPANCIES`
- one or more discrepancy entries appear

### SQL-driven discrepancy testing

Some discrepancy scenarios are easiest to reproduce with direct SQL manipulation, for example:

- `AMOUNT_MISMATCH`
- `MISSING_LEDGER_ENTRY`
- `ORPHANED_LEDGER_ENTRY`

Those are valid for deeper testing, but they are not required for the normal API happy path.

## 9. Quick Manual Test Checklist

1. Get `admin`, `merchant`, and optionally `auditor` tokens.
2. Create payer and payee accounts as admin.
3. Save both account IDs.
4. Initiate a payment as merchant and save the reference.
5. Process the payment as admin.
6. Settle the payment as admin.
7. Run reconciliation and confirm `discrepancyCount = 0`.
8. Fetch the reconciliation report and confirm no discrepancies.
9. Reverse the payment as admin.
10. Fetch the payment and confirm `status = REVERSED`.
11. Re-run reconciliation and confirm success.
12. Run selected negative tests for auth, roles, idempotency, balance, and invalid transitions.

## 10. Related Files

- `payment-gateway/src/test/resources/test-data/manual-e2e-flow-test-data.json`
- `payment-gateway/postman/Mini Payment Gateway.postman_collection.json`
- `payment-gateway/src/test/java/com/minipaygateway/PaymentLifecycleEndToEndFlowIntegrationTest.java`

