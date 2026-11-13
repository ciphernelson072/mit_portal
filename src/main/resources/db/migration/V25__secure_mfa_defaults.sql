-- The previous default marked every account MFA-enabled despite there being no
-- enrollment. Preserve only complete RFC 4648 Base32 TOTP seeds.
UPDATE users
SET mfa_enabled = 0, mfa_secret = NULL
WHERE mfa_secret IS NULL OR mfa_secret NOT REGEXP '^[A-Z2-7]{32}$';

ALTER TABLE users ALTER mfa_enabled SET DEFAULT 0;
