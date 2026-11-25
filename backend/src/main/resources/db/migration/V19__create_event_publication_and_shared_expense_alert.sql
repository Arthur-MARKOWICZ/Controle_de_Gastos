-- Event Publication Registry do Spring Modulith 2.x (ADR-0033). Guarda eventos
-- entre módulos até o listener concluir; concluídos são apagados (completion-mode: delete).
CREATE TABLE event_publication (
    id UUID PRIMARY KEY,
    listener_id TEXT NOT NULL,
    event_type TEXT NOT NULL,
    serialized_event TEXT NOT NULL,
    publication_date TIMESTAMPTZ NOT NULL,
    completion_date TIMESTAMPTZ,
    status TEXT,
    completion_attempts INTEGER NOT NULL DEFAULT 0,
    last_resubmission_date TIMESTAMPTZ
);

CREATE INDEX event_publication_incomplete_idx
    ON event_publication (publication_date)
    WHERE completion_date IS NULL;

-- Alerta de gasto de participante em verba compartilhada (ADR-0026, ADR-0033).
ALTER TABLE in_app_alert DROP CONSTRAINT in_app_alert_alert_type_check;
ALTER TABLE in_app_alert
    ADD CONSTRAINT in_app_alert_alert_type_check CHECK (alert_type IN ('NEGATIVE_BALANCE', 'SHARED_EXPENSE'));

-- Só o saldo negativo é único por verba e mês; cada gasto de participante gera o seu.
DROP INDEX in_app_alert_active_negative_uq;
CREATE UNIQUE INDEX in_app_alert_active_negative_uq
    ON in_app_alert (owner_id, envelope_id, reference_month)
    WHERE dismissed_at IS NULL AND resolved_at IS NULL AND alert_type = 'NEGATIVE_BALANCE';
