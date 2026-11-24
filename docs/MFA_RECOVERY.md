# MFA Recovery Procedure

MFA uses an encrypted TOTP seed and is optional for every role by default.
Production keeps `MFA_ENABLED=true` so users can opt in, with
`MFA_REQUIRED_FOR_PRIVILEGED=false` and `MFA_REQUIRED_FOR_ALL=false`. Users
manage their own enrollment from Account Security; only an account with MFA
enabled is asked for a code at sign-in. Existing enrolled accounts remain
enabled until their owner disables MFA with their password and current code.
Production requires a persistent Base64-encoded 32-byte `MFA_ENCRYPTION_KEY`
while the feature is enabled.

## User enrollment

1. Keep `MFA_ENABLED=true`, `MFA_REQUIRED_FOR_PRIVILEGED=false`, and
   `MFA_REQUIRED_FOR_ALL=false` for an optional, user-controlled rollout.
2. Store the encryption key in the deployment secret manager. Never place it in
   source control, logs, browser output, or a database backup alone.
3. Back up the key through the institution's normal encrypted secret-rotation
   process and verify that a replacement environment can decrypt a test seed
   with the backed-up key.
4. The user starts setup from Account Security and confirms their current
   password. The portal creates the TOTP QR locally and returns it only in the
   authenticated response; no external QR-code service receives the seed.
5. The user scans the QR with an authenticator app, opens the compatible
   `otpauth` link, or enters the manual setup key. Official Google and Microsoft
   app-store links are provided for users who need to install an app.
6. Require a current six-digit code to confirm setup. Show recovery codes once
   and instruct the user to store them privately. Once enabled, only that
   account needs a TOTP or recovery code at sign-in.
7. Users can turn MFA off in Account Security after confirming their password
   and current code. This revokes recovery codes and active sessions.
8. Treat current TOTP seeds and recovery codes as high-sensitivity credentials.
   Never email, paste, or share them with support or another user.

The application issues ten hashed, one-time recovery codes after MFA verification.
Plaintext codes are shown once and are never stored in the database. Codes can be
used only for MFA sign-in, are consumed atomically, and are revoked when MFA is
disabled or set up again. The login screen accepts either a six-digit authenticator
code or a saved recovery code. If all authenticators and recovery codes are
lost, support must verify identity and record the recovery action before an
authorized account administrator performs recovery.

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
