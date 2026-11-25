# MFA Recovery Procedure

MFA uses an encrypted TOTP seed and is optional for every role by default.
Production keeps `MFA_ENABLED=true` so users can opt in, with
`MFA_REQUIRED_FOR_PRIVILEGED=false` and `MFA_REQUIRED_FOR_ALL=false`. Users
manage their own enrollment from Account Security; only an account with MFA
enabled is asked for a code at sign-in. Existing enrolled accounts remain
enabled until their owner disables MFA with their password and current code.
Production requires a persistent Base64-encoded 32-byte `MFA_ENCRYPTION_KEY`
while the feature is enabled.

## Latest release and reported 403 (24 November 2026)

Render reported deployment `dep-dc3117h5efls739467gg` live on image
`sha-a418a654694006fdadb432cb06ce25161455a8ab` (commit
`a418a654694006fdadb432cb06ce25161455a8ab`). The screenshot shows MFA
reported as disabled while the setup, verify, and disable actions are all
visible. The root cause in the page is that shared button CSS sets
`display: inline-flex`, which overrides the browser's default `[hidden]`
styling. This can expose the disable action while MFA is off and the verify
action before a setup secret has been generated. The backend's 403 checks for
enforced MFA and restricted enrollment are intentional security boundaries;
they must not be removed to hide a UI defect.

The source correction hides all `[hidden]` descendants in the account-security
page, adds a regression assertion, and bumps the service-worker cache to v39
so clients receive the corrected UI. Client handlers also refuse verification
without a locally received setup secret and refuse to send a disable request
when MFA status is false. The backend returns a clear 409 response if
verification is attempted before setup; server-side authorization checks stay
in place. Focused backend tests passed (96 tests), frontend unit tests passed
(29), and JavaScript syntax validation passed (41 files). GitHub Actions run
[134](https://github.com/ciphernelson072/mit_portal/actions/runs/45484000672)
passed and published image
`docker.io/ciphernelson/mit-portal:sha-05874d1e450c44e6dabcdbcb3132be0ef1e8c6be`.
The follow-up documentation commit passed run
[135](https://github.com/ciphernelson072/mit_portal/actions/runs/45487818308)
and was published as the deployed image above. Render reports
`Deploy succeeded | Live`; the account-security page, CSS, profile-security
script, and service worker were checked. The CSS contains the hidden-state
rule and the service worker uses cache v39. On 25 November 2026, both public
actuator health endpoints returned HTTP 200 with status `UP`. An unauthenticated
request to the account-security page redirected to the login-required response;
authenticated MFA acceptance remains pending. Refresh/update the portal and
use the flow shown by the state: generate a setup QR/key first,
scan it, then submit the current password and a current six-digit code to
enable MFA. Do not select “Turn off” when status says MFA is disabled. A prior
pending setup seed appeared in a screenshot; replace it with a fresh setup key
and do not reuse the exposed one. If the failure persists, capture the failing
request path and response body without exposing passwords, OTPs, cookies, QR
codes, or seeds.

### Follow-up verification failure (25 November 2026)

An approved test-account sign-in succeeded and the setup endpoint returned a
QR, but clicking “Verify and enable” returned HTTP 403. The TOTP verification
controller returns 401 for a wrong code and has no 403 response; the denial
therefore occurs before TOTP verification. The browser session had been open
for over an hour, matching the default one-hour access-token lifetime. The
likely cause is an expired access token reaching method-level authorization,
which mapped anonymous denial to 403 and prevented the frontend's 401
refresh-and-retry flow. The response body and authentication cookie were not
inspected, so expiry remains a likely cause rather than a proven fact.

The source now maps anonymous or unauthenticated access-denied exceptions to
401, allowing the existing refresh-and-retry flow to run; authenticated
authorization denials remain 403. All 90 `SecurityIntegrationTest` tests pass,
including the MFA enrollment-policy test. This correction has not yet passed
CI or been deployed. Do not treat the account as MFA-enabled. After deployment,
sign in again, replace the pending setup key with a fresh one, and verify using
a current code. Never share the QR, secret, password, or OTP.

## User enrollment

1. Keep `MFA_ENABLED=true`, `MFA_REQUIRED_FOR_PRIVILEGED=false`, and
   `MFA_REQUIRED_FOR_ALL=false` for an optional, user-controlled rollout.
2. Store the encryption key in the deployment secret manager. Never place it in
   source control, server logs, browser console output, or a database backup alone.
3. Back up the key through the institution's normal encrypted secret-rotation
   process and verify that a replacement environment can decrypt a test seed
   with the backed-up key.
4. The user starts setup from Account Security and confirms their current
   password. The portal creates the TOTP QR locally and returns it only in the
   authenticated response; no external QR-code service receives the seed.
   Starting setup again replaces any pending seed, so an exposed setup key can
   be invalidated before MFA is enabled. Replacing a pending seed does not turn
   off an already-enabled authenticator.
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
