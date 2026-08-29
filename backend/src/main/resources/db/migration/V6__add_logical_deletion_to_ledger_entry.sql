ALTER TABLE ledger_entry ADD COLUMN deleted_at TIMESTAMPTZ;

CREATE INDEX ledger_entry_active_occurred_idx
    ON ledger_entry (occurred_at DESC, created_at DESC)
    WHERE deleted_at IS NULL;
