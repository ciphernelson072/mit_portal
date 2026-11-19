-- Existing access JWTs are not tied to refresh-token rows. Revoke every prior
-- session and advance credential versions so only a fresh login remains valid.
UPDATE refresh_tokens SET revoked = TRUE WHERE revoked = FALSE;

UPDATE users
SET credential_version = credential_version + 1;
