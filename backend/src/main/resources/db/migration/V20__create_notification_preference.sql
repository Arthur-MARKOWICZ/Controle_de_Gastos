-- Consentimento por tipo de notificação e horário do lembrete diário (ADR-0033).
-- Sem linha, valem os padrões: tudo desligado e lembrete às 20:00 (America/Sao_Paulo).
CREATE TABLE notification_preference (
    user_id UUID PRIMARY KEY REFERENCES user_account(id) ON DELETE CASCADE,
    daily_reminder BOOLEAN NOT NULL DEFAULT FALSE,
    negative_balance BOOLEAN NOT NULL DEFAULT FALSE,
    shared_expense BOOLEAN NOT NULL DEFAULT FALSE,
    reminder_time TIME NOT NULL DEFAULT '20:00',
    last_reminder_date DATE,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT notification_preference_reminder_step_ck
        CHECK (EXTRACT(MINUTE FROM reminder_time) IN (0, 15, 30, 45) AND EXTRACT(SECOND FROM reminder_time) = 0)
);

CREATE INDEX notification_preference_daily_reminder_idx
    ON notification_preference (reminder_time)
    WHERE daily_reminder;
