# MFA Recovery Procedure

MFA uses an encrypted TOTP seed and is disabled by default. Enabling it requires
an approved institution policy and a persistent Base64-encoded 32-byte
`MFA_ENCRYPTION_KEY`; the application must reject startup when MFA is enabled
without a valid key.

## Before enabling MFA

1. Approve MFA scope by user role and document any mandatory exclusions.
2. Store the encryption key in the deployment secret manager. Never place it in
   source control, logs, browser output, or a database backup alone.
3. Back up the key through the institution's normal encrypted secret-rotation
   process and verify that current and replacement environments have the same key.
4. Publish a controlled support process for lost authenticators. Self-service
   password recovery must not bypass MFA or permit account takeover.
5. Treat current TOTP seeds and any recovery codes as high-sensitivity
   credentials. Recovery codes require separate persistent storage, hashing,
   one-time consumption, and replay/audit controls before being enabled.

## Locked-account recovery

1. Verify identity through the institution's approved support channel.
2. Require independent authorization from a designated security or account
   administrator.
3. Disable MFA only for the verified account, record the support ticket, actor,
   reason, target account, and timestamp in audit logs, then require the user to
   enroll a new authenticator.
4. Revoke all sessions and refresh tokens during recovery.
5. Require an immediate password change if credentials may also be compromised.
6. Notify the account owner through a pre-registered email address and require
   confirmation of the recovery action.

Do not email or paste TOTP seeds or recovery codes. Do not reset MFA through
impersonation, shared administrator credentials, or an unaudited direct
database edit.
