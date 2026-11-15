CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX ledger_entry_description_trgm_idx
    ON ledger_entry USING gin (lower(description) gin_trgm_ops);
