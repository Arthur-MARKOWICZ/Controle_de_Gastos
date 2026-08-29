CREATE TABLE envelope (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES user_account(id) ON DELETE CASCADE,
    name VARCHAR(80) NOT NULL,
    purpose VARCHAR(20) NOT NULL CHECK (purpose IN ('LIMIT', 'GOAL', 'FIXED')),
    base_amount NUMERIC(19, 2) NOT NULL CHECK (base_amount >= 0),
    created_at TIMESTAMPTZ NOT NULL,
    archived_at TIMESTAMPTZ,
    version BIGINT NOT NULL
);

CREATE INDEX envelope_owner_idx ON envelope(owner_id);
CREATE INDEX envelope_owner_archived_idx ON envelope(owner_id, archived_at);

CREATE TABLE envelope_participant (
    envelope_id UUID NOT NULL REFERENCES envelope(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES user_account(id) ON DELETE CASCADE,
    added_at TIMESTAMPTZ NOT NULL,
    added_by UUID NOT NULL REFERENCES user_account(id),
    PRIMARY KEY (envelope_id, user_id)
);

CREATE INDEX envelope_participant_user_idx ON envelope_participant(user_id);
