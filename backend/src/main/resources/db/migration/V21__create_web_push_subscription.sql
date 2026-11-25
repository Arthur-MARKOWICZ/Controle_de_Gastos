-- Assinaturas Web Push dos navegadores do titular (ADR-0033). Endpoint e chaves
-- nunca entram em logs; a exportação do titular não inclui p256dh/auth.
CREATE TABLE web_push_subscription (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES user_account(id) ON DELETE CASCADE,
    endpoint TEXT NOT NULL UNIQUE,
    p256dh VARCHAR(100) NOT NULL,
    auth VARCHAR(40) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    last_success_at TIMESTAMPTZ
);

CREATE INDEX web_push_subscription_user_idx ON web_push_subscription (user_id);
