-- PRD §6.3 — core tables (PostgreSQL 15)

CREATE TABLE accounts (
    id              BIGSERIAL PRIMARY KEY,
    owner_ref       VARCHAR(255) NOT NULL,
    currency        VARCHAR(3) NOT NULL,
    account_type    VARCHAR(32) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE payment_transactions (
    id                  BIGSERIAL PRIMARY KEY,
    reference           VARCHAR(64) NOT NULL UNIQUE,
    payer_account_id    BIGINT NOT NULL REFERENCES accounts (id),
    payee_account_id    BIGINT NOT NULL REFERENCES accounts (id),
    amount              NUMERIC(19, 4) NOT NULL,
    currency            VARCHAR(3) NOT NULL,
    status              VARCHAR(32) NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_payment_transactions_payer ON payment_transactions (payer_account_id);
CREATE INDEX idx_payment_transactions_payee ON payment_transactions (payee_account_id);
CREATE INDEX idx_payment_transactions_status ON payment_transactions (status);

CREATE TABLE ledger_entries (
    id                  BIGSERIAL PRIMARY KEY,
    debit_account_id  BIGINT NOT NULL REFERENCES accounts (id),
    credit_account_id BIGINT NOT NULL REFERENCES accounts (id),
    amount              NUMERIC(19, 4) NOT NULL,
    currency            VARCHAR(3) NOT NULL,
    transaction_id      BIGINT NOT NULL REFERENCES payment_transactions (id),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ledger_entries_transaction ON ledger_entries (transaction_id);
CREATE INDEX idx_ledger_entries_debit ON ledger_entries (debit_account_id);
CREATE INDEX idx_ledger_entries_credit ON ledger_entries (credit_account_id);

CREATE TABLE idempotency_keys (
    key             UUID PRIMARY KEY,
    request_hash    VARCHAR(64) NOT NULL,
    http_status     SMALLINT NOT NULL,
    response_body   TEXT NOT NULL,
    expires_at      TIMESTAMPTZ NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_idempotency_keys_expires_at ON idempotency_keys (expires_at);

CREATE TABLE audit_log (
    id          BIGSERIAL PRIMARY KEY,
    entity_id   VARCHAR(64) NOT NULL,
    old_status  VARCHAR(32),
    new_status  VARCHAR(32),
    actor       VARCHAR(255) NOT NULL,
    ip          VARCHAR(45),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_log_entity ON audit_log (entity_id);

CREATE TABLE reconciliation_reports (
    id                  BIGSERIAL PRIMARY KEY,
    period_start        DATE NOT NULL,
    period_end          DATE NOT NULL,
    currency            VARCHAR(3) NOT NULL,
    discrepancy_count   INTEGER NOT NULL DEFAULT 0,
    status              VARCHAR(32) NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_reconciliation_reports_period ON reconciliation_reports (period_start, period_end, currency);
