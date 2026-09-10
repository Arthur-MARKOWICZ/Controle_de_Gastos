-- Código de handoff do login social em cliente nativo (ADR-020).
-- O navegador do sistema recebe o código pelo App Link e o aplicativo o troca
-- pelos tokens. Apenas o hash é persistido, como no password_reset_token.
ALTER TABLE oauth_authorization_state
    ADD COLUMN client VARCHAR(16) NOT NULL DEFAULT 'WEB';

ALTER TABLE oauth_authorization_state
    ALTER COLUMN client DROP DEFAULT;

CREATE TABLE oauth_mobile_handoff (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES user_account(id) ON DELETE CASCADE,
    code_hash VARCHAR(64) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ
);

CREATE INDEX oauth_mobile_handoff_expiry_idx
    ON oauth_mobile_handoff(expires_at)
    WHERE consumed_at IS NULL;
