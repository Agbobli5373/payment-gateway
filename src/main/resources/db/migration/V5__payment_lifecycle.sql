-- Epic 5 — payment lifecycle timestamps and query support

ALTER TABLE payment_transactions
    ADD COLUMN settled_at TIMESTAMPTZ NULL,
    ADD COLUMN failed_at TIMESTAMPTZ NULL,
    ADD COLUMN reversed_at TIMESTAMPTZ NULL;

CREATE INDEX idx_payment_transactions_created_at ON payment_transactions (created_at);

-- SYSTEM_FLOAT accounts are created lazily per currency (see AccountService.getOrCreateSystemFloat).
