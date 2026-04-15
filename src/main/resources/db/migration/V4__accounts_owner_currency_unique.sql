-- One logical account per (owner_ref, currency); prevents duplicate SYSTEM_SUSPENSE races.
CREATE UNIQUE INDEX uq_accounts_owner_ref_currency ON accounts (owner_ref, currency);
