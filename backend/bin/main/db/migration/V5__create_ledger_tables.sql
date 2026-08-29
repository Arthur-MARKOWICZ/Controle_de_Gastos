CREATE TABLE ledger_entry (
    id UUID PRIMARY KEY,
    envelope_id UUID NOT NULL REFERENCES envelope(id) ON DELETE CASCADE,
    owner_id UUID NOT NULL,
    author_id UUID NOT NULL REFERENCES user_account(id),
    amount NUMERIC(19, 2) NOT NULL CHECK (amount > 0),
    kind VARCHAR(12) NOT NULL CHECK (kind IN ('EXPENSE', 'CONTRIBUTION')),
    occurred_at DATE NOT NULL,
    description VARCHAR(140),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX ledger_envelope_occurred_idx ON ledger_entry(envelope_id, occurred_at);
CREATE INDEX ledger_envelope_kind_idx ON ledger_entry(envelope_id, kind);
CREATE INDEX ledger_owner_idx ON ledger_entry(owner_id);
CREATE INDEX ledger_author_idx ON ledger_entry(author_id);
