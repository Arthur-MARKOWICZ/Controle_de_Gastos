CREATE TABLE recurring_entry (
    id UUID PRIMARY KEY,
    envelope_id UUID NOT NULL REFERENCES envelope(id) ON DELETE CASCADE,
    kind VARCHAR(12) NOT NULL CHECK (kind IN ('EXPENSE', 'CONTRIBUTION')),
    amount NUMERIC(19, 2) NOT NULL CHECK (amount > 0),
    day_of_month INTEGER NOT NULL CHECK (day_of_month BETWEEN 1 AND 31),
    description VARCHAR(140),
    active BOOLEAN NOT NULL,
    first_competence DATE NOT NULL CHECK (EXTRACT(DAY FROM first_competence) = 1),
    created_at TIMESTAMPTZ NOT NULL,
    deleted_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX recurring_entry_envelope_idx ON recurring_entry (envelope_id);
CREATE INDEX recurring_entry_active_idx ON recurring_entry (first_competence)
    WHERE active AND deleted_at IS NULL;

CREATE TABLE recurring_occurrence (
    id UUID PRIMARY KEY,
    recurring_entry_id UUID NOT NULL REFERENCES recurring_entry(id) ON DELETE CASCADE,
    envelope_id UUID NOT NULL REFERENCES envelope(id) ON DELETE CASCADE,
    competence DATE NOT NULL CHECK (EXTRACT(DAY FROM competence) = 1),
    due_date DATE NOT NULL,
    suggested_amount NUMERIC(19, 2) NOT NULL CHECK (suggested_amount > 0),
    status VARCHAR(10) NOT NULL CHECK (status IN ('PENDING', 'CONFIRMED', 'DISCARDED')),
    ledger_entry_id UUID REFERENCES ledger_entry(id) ON DELETE CASCADE,
    resolved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT recurring_occurrence_competence_uk UNIQUE (recurring_entry_id, competence)
);

CREATE INDEX recurring_occurrence_envelope_status_idx
    ON recurring_occurrence (envelope_id, status, due_date);
CREATE INDEX recurring_occurrence_ledger_entry_idx ON recurring_occurrence (ledger_entry_id);
