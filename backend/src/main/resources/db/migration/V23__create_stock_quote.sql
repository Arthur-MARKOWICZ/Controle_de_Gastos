-- Última cotação válida de cada ticker da B3 (ADR-0034 e ADR-0035, issue #77).
-- Dado de referência global, sem owner_id: o preço de um ticker é o mesmo para
-- todos os titulares. Sem FK para stock_lot (lá o ticker não é único); as
-- cotações de tickers sem lote são apagadas no início de cada rodada.
-- quoted_at é o instante de mercado informado pelo provedor; fetched_at, o da consulta.
CREATE TABLE stock_quote (
    ticker VARCHAR(6) PRIMARY KEY CHECK (ticker ~ '^[A-Z]{4}[0-9]{1,2}$'),
    price NUMERIC(19, 2) NOT NULL CHECK (price > 0),
    quoted_at TIMESTAMPTZ NOT NULL,
    fetched_at TIMESTAMPTZ NOT NULL
);
