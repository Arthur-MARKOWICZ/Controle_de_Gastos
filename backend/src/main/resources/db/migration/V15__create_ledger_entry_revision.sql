-- ADR-0027 / issue #36: trilha append-only de edições e exclusões lógicas de gastos.

CREATE TABLE ledger_entry_revision (
    id UUID PRIMARY KEY,
    ledger_entry_id UUID NOT NULL REFERENCES ledger_entry(id) ON DELETE CASCADE,
    owner_id UUID NOT NULL,
    actor_user_id UUID NOT NULL REFERENCES user_account(id) ON DELETE RESTRICT,
    action VARCHAR(12) NOT NULL CHECK (action IN ('EDIT', 'DELETE')),
    previous_envelope_id UUID NOT NULL,
    previous_amount NUMERIC(19, 2) NOT NULL CHECK (previous_amount > 0),
    previous_description VARCHAR(140),
    changed_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX ledger_entry_revision_entry_changed_idx
    ON ledger_entry_revision (ledger_entry_id, changed_at DESC, id DESC);

CREATE INDEX ledger_entry_revision_owner_changed_idx
    ON ledger_entry_revision (owner_id, changed_at DESC, id DESC);
