CREATE TABLE user_account (
    id UUID PRIMARY KEY,
    email_normalized VARCHAR(254) NOT NULL,
    email_verified_at TIMESTAMPTZ,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT user_account_email_normalized_unique UNIQUE (email_normalized),
    CONSTRAINT user_account_status_valid CHECK (status IN ('ACTIVE', 'BLOCKED', 'DELETED'))
);

CREATE TABLE password_credential (
    user_id UUID PRIMARY KEY REFERENCES user_account(id) ON DELETE CASCADE,
    password_hash VARCHAR(255) NOT NULL,
    password_changed_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE auth_session (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES user_account(id) ON DELETE CASCADE,
    refresh_secret_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    last_used_at TIMESTAMPTZ NOT NULL,
    idle_expires_at TIMESTAMPTZ NOT NULL,
    absolute_expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    revocation_reason VARCHAR(32)
);

CREATE INDEX auth_session_user_id_idx ON auth_session(user_id);

CREATE TABLE auth_attempt (
    attempt_key VARCHAR(64) PRIMARY KEY,
    attempt_count INTEGER NOT NULL,
    window_started_at TIMESTAMPTZ NOT NULL,
    blocked_until TIMESTAMPTZ,
    expires_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX auth_attempt_expires_at_idx ON auth_attempt(expires_at);
