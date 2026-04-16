-- Epic 6 — reconciliation report detail + query indexes

ALTER TABLE reconciliation_reports
    ADD COLUMN expected_count   INTEGER        NOT NULL DEFAULT 0,
    ADD COLUMN expected_sum     NUMERIC(19, 4) NOT NULL DEFAULT 0,
    ADD COLUMN actual_count     INTEGER        NOT NULL DEFAULT 0,
    ADD COLUMN actual_sum       NUMERIC(19, 4) NOT NULL DEFAULT 0,
    ADD COLUMN discrepancy_details JSONB      NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN completed_at     TIMESTAMPTZ    NOT NULL DEFAULT NOW();

CREATE INDEX idx_payment_txn_reconcile ON payment_transactions (currency, status, settled_at);

CREATE INDEX idx_ledger_txn_currency ON ledger_entries (transaction_id, currency);
