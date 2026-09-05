ALTER TABLE user_account
    ALTER COLUMN password_hash DROP NOT NULL,
    ALTER COLUMN password_changed_at DROP NOT NULL;

CREATE TABLE identity_provider_link (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES user_account(id) ON DELETE CASCADE,
    provider VARCHAR(16) NOT NULL CHECK (provider IN ('GOOGLE', 'GITHUB')),
    provider_user_id VARCHAR(255) NOT NULL,
    provider_email VARCHAR(254),
    linked_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT identity_provider_link_provider_user_unique UNIQUE (provider, provider_user_id),
    CONSTRAINT identity_provider_link_user_provider_unique UNIQUE (user_id, provider)
);

CREATE TABLE oauth_authorization_state (
    id UUID PRIMARY KEY,
    state_hash VARCHAR(64) NOT NULL UNIQUE,
    provider VARCHAR(16) NOT NULL CHECK (provider IN ('GOOGLE', 'GITHUB')),
    linking_user_id UUID REFERENCES user_account(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ
);

CREATE INDEX oauth_authorization_state_expires_at_idx ON oauth_authorization_state(expires_at);
