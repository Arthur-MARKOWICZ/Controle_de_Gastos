CREATE TABLE monthly_income (
    owner_id UUID NOT NULL REFERENCES user_account(id) ON DELETE CASCADE,
    effective_month DATE NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (owner_id, effective_month),
    CONSTRAINT monthly_income_month_first_day CHECK (EXTRACT(DAY FROM effective_month) = 1),
    CONSTRAINT monthly_income_amount_non_negative CHECK (amount >= 0)
);

CREATE INDEX monthly_income_owner_effective_idx
    ON monthly_income (owner_id, effective_month DESC);

CREATE TABLE income_revision (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES user_account(id) ON DELETE CASCADE,
    actor_user_id UUID NOT NULL REFERENCES user_account(id) ON DELETE CASCADE,
    amount NUMERIC(19, 2) NOT NULL,
    effective_month DATE NOT NULL,
    changed_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT income_revision_month_first_day CHECK (EXTRACT(DAY FROM effective_month) = 1),
    CONSTRAINT income_revision_amount_non_negative CHECK (amount >= 0)
);

CREATE INDEX income_revision_owner_changed_idx
    ON income_revision (owner_id, changed_at DESC, id DESC);
