-- Lotes de compra de ativos da B3 do titular (ADR-0034, issue #76). Isolado do
-- orçamento. Sem ON DELETE CASCADE: a conta é encerrada, não apagada, e o módulo
-- privacy remove os lotes na exclusão do titular. Ticker e valores nunca entram em logs.
CREATE TABLE stock_lot (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES user_account(id),
    ticker VARCHAR(6) NOT NULL CHECK (ticker ~ '^[A-Z]{4}[0-9]{1,2}$'),
    quantity BIGINT NOT NULL CHECK (quantity > 0),
    unit_price NUMERIC(19, 2) NOT NULL CHECK (unit_price > 0),
    purchase_date DATE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX stock_lot_owner_idx ON stock_lot (owner_id, purchase_date);
