CREATE TABLE in_app_alert (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES user_account(id) ON DELETE CASCADE,
    envelope_id UUID NOT NULL,
    envelope_name VARCHAR(80) NOT NULL,
    alert_type VARCHAR(32) NOT NULL CHECK (alert_type IN ('NEGATIVE_BALANCE')),
    reference_month DATE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    dismissed_at TIMESTAMPTZ,
    resolved_at TIMESTAMPTZ
);

CREATE INDEX in_app_alert_owner_active_idx
    ON in_app_alert (owner_id, created_at DESC)
    WHERE dismissed_at IS NULL AND resolved_at IS NULL;

CREATE UNIQUE INDEX in_app_alert_active_negative_uq
    ON in_app_alert (owner_id, envelope_id, reference_month, alert_type)
    WHERE dismissed_at IS NULL AND resolved_at IS NULL;
