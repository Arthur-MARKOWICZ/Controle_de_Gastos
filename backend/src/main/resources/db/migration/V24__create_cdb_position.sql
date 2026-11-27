-- Posições de CDB prefixado do titular (ADR-0034, issue #78). Isolado do orçamento.
-- Sem ON DELETE CASCADE: a limpeza fica no módulo privacy, como em stock_lot.
-- Principal, taxa e estimado nunca entram em logs.
CREATE TABLE cdb_position (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES user_account(id),
    principal NUMERIC(19, 2) NOT NULL CHECK (principal > 0),
    annual_rate NUMERIC(7, 4) NOT NULL CHECK (annual_rate > 0),
    start_date DATE NOT NULL,
    maturity_date DATE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CHECK (maturity_date > start_date)
);

CREATE INDEX cdb_position_owner_idx ON cdb_position (owner_id, start_date);
