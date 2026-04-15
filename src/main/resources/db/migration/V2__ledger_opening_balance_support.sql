-- Opening balances and FR-LED-03: optional payment link + business reference

ALTER TABLE ledger_entries
    ALTER COLUMN transaction_id DROP NOT NULL;

ALTER TABLE ledger_entries
    ADD COLUMN reference VARCHAR(64) NOT NULL DEFAULT 'LEGACY';

ALTER TABLE ledger_entries
    ALTER COLUMN reference DROP DEFAULT;
