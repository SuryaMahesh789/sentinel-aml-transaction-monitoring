-- =============================================================================
-- V3__case_management_columns.sql
-- Add investigation_notes and resolution_reason to cases table.
-- Also add index on case_alerts.alert_id for duplicate-case checks.
-- =============================================================================

ALTER TABLE cases
    ADD COLUMN IF NOT EXISTS investigation_notes TEXT,
    ADD COLUMN IF NOT EXISTS resolution_reason   TEXT;

-- Index to support efficient duplicate-active-case lookup per alert
CREATE INDEX IF NOT EXISTS idx_case_alerts_alert_id ON case_alerts(alert_id);
