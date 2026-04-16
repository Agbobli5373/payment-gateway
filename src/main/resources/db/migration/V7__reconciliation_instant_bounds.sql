-- Persist exact reconcile window (UTC instants) alongside calendar period_* dates.

ALTER TABLE reconciliation_reports
    ADD COLUMN reconcile_from TIMESTAMPTZ,
    ADD COLUMN reconcile_to   TIMESTAMPTZ;
