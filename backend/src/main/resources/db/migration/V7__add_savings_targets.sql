ALTER TABLE envelope DROP CONSTRAINT envelope_purpose_check;

ALTER TABLE envelope
    ADD COLUMN target_amount NUMERIC(19, 2),
    ADD COLUMN target_reached_at TIMESTAMPTZ,
    ADD CONSTRAINT envelope_purpose_check
        CHECK (purpose IN ('LIMIT', 'GOAL', 'FIXED', 'SAVINGS_TARGET')),
    ADD CONSTRAINT envelope_savings_target_check
        CHECK (
            (purpose = 'SAVINGS_TARGET' AND base_amount = 0 AND target_amount IS NOT NULL AND target_amount > 0)
            OR
            (purpose <> 'SAVINGS_TARGET' AND target_amount IS NULL AND target_reached_at IS NULL)
        );
