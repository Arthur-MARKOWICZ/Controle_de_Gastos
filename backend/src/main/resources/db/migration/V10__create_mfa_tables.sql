CREATE TABLE totp_credential (
    user_id UUID PRIMARY KEY REFERENCES user_account(id) ON DELETE CASCADE,
    status VARCHAR(16) NOT NULL CHECK (status IN ('DISABLED','PENDING','ENABLED')),
    secret_ciphertext BYTEA,
    secret_nonce BYTEA,
    key_version INTEGER,
    created_at TIMESTAMPTZ NOT NULL,
    confirmed_at TIMESTAMPTZ,
    pending_expires_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL
);

INSERT INTO totp_credential (user_id, status, created_at, updated_at)
SELECT id, 'DISABLED', now(), now() FROM user_account;

CREATE TABLE recovery_code (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES user_account(id) ON DELETE CASCADE,
    code_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    invalidated_at TIMESTAMPTZ,
    CONSTRAINT recovery_code_user_hash_unique UNIQUE (user_id, code_hash)
);

CREATE INDEX recovery_code_user_active_idx ON recovery_code(user_id)
    WHERE consumed_at IS NULL AND invalidated_at IS NULL;

CREATE TABLE mfa_login_challenge (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES user_account(id) ON DELETE CASCADE,
    challenge_hash VARCHAR(64) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    invalidated_at TIMESTAMPTZ
);

CREATE INDEX mfa_login_challenge_user_active_idx ON mfa_login_challenge(user_id)
    WHERE consumed_at IS NULL AND invalidated_at IS NULL;
