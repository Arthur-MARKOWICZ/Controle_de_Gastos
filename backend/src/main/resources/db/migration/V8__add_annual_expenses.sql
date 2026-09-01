ALTER TABLE envelope DROP CONSTRAINT envelope_savings_target_check;
ALTER TABLE envelope DROP CONSTRAINT envelope_purpose_check;

ALTER TABLE envelope
    ADD COLUMN annual_amount NUMERIC(19, 2),
    ADD COLUMN annual_due_month INTEGER,
    ADD COLUMN annual_due_day INTEGER,
    ADD COLUMN annual_funding_mode VARCHAR(12),
    ADD CONSTRAINT envelope_purpose_check
        CHECK (purpose IN ('LIMIT', 'GOAL', 'FIXED', 'SAVINGS_TARGET', 'ANNUAL_EXPENSE')),
    ADD CONSTRAINT envelope_financial_configuration_check
        CHECK (
            (purpose = 'SAVINGS_TARGET' AND base_amount = 0 AND target_amount IS NOT NULL AND target_amount > 0
                AND annual_amount IS NULL AND annual_due_month IS NULL AND annual_due_day IS NULL AND annual_funding_mode IS NULL)
            OR
            (purpose = 'ANNUAL_EXPENSE' AND base_amount = 0 AND target_amount IS NULL AND target_reached_at IS NULL
                AND annual_amount > 0 AND annual_due_month BETWEEN 1 AND 12 AND annual_due_day BETWEEN 1 AND 31
                AND annual_funding_mode IN ('MONTHLY', 'ONE_TIME'))
            OR
            (purpose IN ('LIMIT', 'GOAL', 'FIXED') AND target_amount IS NULL AND target_reached_at IS NULL
                AND annual_amount IS NULL AND annual_due_month IS NULL AND annual_due_day IS NULL AND annual_funding_mode IS NULL)
        );
