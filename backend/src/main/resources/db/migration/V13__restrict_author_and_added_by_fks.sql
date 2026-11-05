-- DB-005 / ADR-0023: tornar explícito que autoria e added_by não cascateiam
-- a exclusão física da conta. A exclusão do titular é lógica (UUID preservado).

ALTER TABLE ledger_entry
    DROP CONSTRAINT ledger_entry_author_id_fkey;

ALTER TABLE ledger_entry
    ADD CONSTRAINT ledger_entry_author_id_fkey
        FOREIGN KEY (author_id) REFERENCES user_account (id) ON DELETE RESTRICT;

ALTER TABLE envelope_participant
    DROP CONSTRAINT envelope_participant_added_by_fkey;

ALTER TABLE envelope_participant
    ADD CONSTRAINT envelope_participant_added_by_fkey
        FOREIGN KEY (added_by) REFERENCES user_account (id) ON DELETE RESTRICT;
