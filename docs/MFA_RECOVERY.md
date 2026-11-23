# MFA Recovery Procedure

MFA uses an encrypted TOTP seed. Generic development defaults keep it opt-in. Production requires MFA for
privileged accounts (administrators and accounts with active finance
permissions), while other users may enroll optionally. This policy requires
an approved institution policy and a persistent Base64-encoded 32-byte
`MFA_ENCRYPTION_KEY`; production startup rejects a missing/invalid key and
rejects attempts to disable privileged MFA. Users may enroll from Account
Security; enrolled users must verify MFA when signing in. Set
`MFA_REQUIRED_FOR_ALL=true` only when the institution explicitly chooses
mandatory enrollment for every user. It requires every role to enroll and
complete MFA before portal access. It also requires
`MFA_ENABLED=true` and a valid persistent encryption key.

## Staged rollout

1. Approve the privileged-account scope and the support/recovery process.
2. Store the encryption key in the deployment secret manager. Never place it in
   source control, logs, browser output, or a database backup alone.
3. Back up the key through the institution's normal encrypted secret-rotation
   process and verify that a replacement environment can decrypt a test seed
   with the backed-up key.
4. Before production rollout, confirm every privileged account has a working
   authenticator or can complete enrollment at sign-in. Production deploys
   require `MFA_ENABLED=true`, `MFA_REQUIRED_FOR_PRIVILEGED=true`, and
   `MFA_REQUIRED_FOR_ALL=false` by default.
5. Confirm that representative student, teacher, administrator, and
   finance-privileged accounts are enrolled. Verify that sign-in and refresh
   require a completed MFA challenge and rehearse the approved recovery process.
   Keep `MFA_REQUIRED_FOR_ALL=false` unless institution-wide enforcement is
   explicitly approved. Production cannot disable privileged-account
   enforcement.
6. Users who are not enrolled when enforcement is activated receive a
   short-lived, enrollment-only session. It can reach only MFA status/setup/
   verification, logout, and (when required) password change; it cannot reach
   portal data or obtain a refresh token.
7. Publish a controlled support process for lost authenticators. Self-service
   password recovery must not bypass MFA or permit account takeover.
8. Treat current TOTP seeds and any recovery codes as high-sensitivity
   credentials. Recovery codes require separate persistent storage, hashing,
   one-time consumption, and replay/audit controls before being enabled.

The application issues ten hashed, one-time recovery codes after MFA verification.
Plaintext codes are shown once and are never stored in the database. Codes can be
used only for MFA sign-in, are consumed atomically, and are revoked when MFA is
disabled or set up again. The login screen accepts either a six-digit authenticator
code or a saved recovery code. Administrators may still use support-assisted
recovery for a lost authenticator; a required privileged or universally enforced
account cannot disable MFA through its own account-security page.

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
