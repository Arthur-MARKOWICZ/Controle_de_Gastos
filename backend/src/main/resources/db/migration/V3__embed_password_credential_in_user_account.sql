ALTER TABLE user_account
    ADD COLUMN password_hash VARCHAR(255),
    ADD COLUMN password_changed_at TIMESTAMPTZ;

UPDATE user_account AS account
SET password_hash = credential.password_hash,
    password_changed_at = credential.password_changed_at
FROM password_credential AS credential
WHERE credential.user_id = account.id;

ALTER TABLE user_account
    ALTER COLUMN password_hash SET NOT NULL,
    ALTER COLUMN password_changed_at SET NOT NULL;

DROP TABLE password_credential;
