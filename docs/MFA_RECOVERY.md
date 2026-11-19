# MFA Recovery Procedure

MFA uses an encrypted TOTP seed and is disabled by default. Enabling it requires
an approved institution policy and a persistent Base64-encoded 32-byte
`MFA_ENCRYPTION_KEY`; the application must reject startup when MFA is enabled
without a valid key. `MFA_REQUIRED_FOR_PRIVILEGED` is a separate, default-off
switch for enforcing enrollment on administrator accounts and accounts with
active finance permissions. It cannot be enabled unless `MFA_ENABLED=true`.

## Staged rollout

1. Approve the privileged-account scope and the support/recovery process.
2. Store the encryption key in the deployment secret manager. Never place it in
   source control, logs, browser output, or a database backup alone.
3. Back up the key through the institution's normal encrypted secret-rotation
   process and verify that a replacement environment can decrypt a test seed
   with the backed-up key.
4. Deploy with `MFA_ENABLED=true` and `MFA_REQUIRED_FOR_PRIVILEGED=false`.
   Privileged users can then enroll at Account Security without changing their
   existing sign-in policy.
5. Confirm that every administrator and finance-privileged account is enrolled,
   verify that sign-in and refresh require a completed MFA challenge, and
   rehearse the approved recovery process. Only then set
   `MFA_REQUIRED_FOR_PRIVILEGED=true`.
6. Users who are not enrolled when enforcement is activated receive a
   short-lived, enrollment-only session. It can reach only MFA status/setup/
   verification, logout, and (when required) password change; it cannot reach
   portal data or obtain a refresh token.
7. Publish a controlled support process for lost authenticators. Self-service
   password recovery must not bypass MFA or permit account takeover.
8. Treat current TOTP seeds and any recovery codes as high-sensitivity
   credentials. Recovery codes require separate persistent storage, hashing,
   one-time consumption, and replay/audit controls before being enabled.

The application does not currently issue recovery codes or provide an
administrator self-service reset. Do not promise recovery-code support until
that workflow has been implemented and tested. A required privileged account
cannot disable MFA through its own account-security page.

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
