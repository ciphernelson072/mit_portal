# Security Changes Summary

## Permission model

The project now uses a blended authorization model:

- Defense-in-depth role checks remain in the security config and on obvious admin-only routes.
- Controller-level decisions use `@permissionSecurity.*` checks for delegated operations.
- Ownership checks remain in place for user-scoped resources and course/student access boundaries.

This keeps admin fallback compatibility while allowing delegated grants such as `PERM_FINANCE_PAYMENT_CONFIRM`, `PERM_REPORTS_VIEW`, and `PERM_ADMISSIONS_REVIEW` to be assigned independently.

## Role and dashboard boundaries

The application roles are `ADMIN`, `TEACHER`, and `STUDENT`. At method level,
`ADMIN` inherits the teacher and student authorities for supervisory access;
teacher and student do not inherit one another or admin authority. API path rules
and controller ownership/permission checks remain in force, so hierarchy inheritance
does not replace resource-level authorization.

Server-side static-page rules now match the role guards in the frontend. Student
dashboard and student-only modules (including announcements, documents, hostel,
library, notifications, research, scholarships, and support tickets) require the
`STUDENT` authority. Teacher and admin dashboard shells retain their own role gates.
Browser role values are UI hints only; they do not grant access. The
`SecurityIntegrationTest` suite checks positive dashboard access, cross-role
denials, student-only page access, and API separation.

Course registration is constrained by the student's active programme curriculum
for the academic year, and student section listings apply that same curriculum
boundary. Programme selection also requires an active department, faculty, and
campus. Course-material and teacher-to-student access checks recognize active
course registrations as well as the legacy course roster, so dropping a current
registration removes the new enrollment-based access path without breaking
existing roster data.

Teachers now have a normalized home-department assignment through
`teachers.department_id`. New hires and updates must select a department in an
active campus/faculty hierarchy. This is an organizational assignment only:
teaching rights continue to come from explicit course assignments, allowing a
teacher to teach across departments without extending access based on their home
department. Existing free-text teacher department values are preserved as subject
specializations and are not automatically mapped to departments because those
values may be ambiguous.

Programme discovery and admission now require the complete campus → faculty →
department → programme chain to be active. This is enforced both when public
applicants list or submit a programme and when staff enroll an accepted applicant;
authenticated institution directory reads also omit records whose ancestors are
inactive. A programme accepted earlier cannot create a new student enrollment
after any part of its directory hierarchy has been deactivated.

## Added permission scopes

The catalog now includes permission codes used by the delegated access rules, including:

- `FINANCE_HOLD_MANAGE`
- `FINANCE_PAYMENT_CONFIRM`
- `FINANCE_REFUND`
- `FINANCE_RECONCILIATION`
- `FINANCE_WAIVER`
- `PROCUREMENT_MANAGE`
- `ADMISSIONS_REVIEW`
- `REGISTRATION_MANAGE`
- `EXAM_PERMIT_MANAGE`
- `EXAM_RESULT_PUBLISH`
- `EXAM_MANAGE`
- `ASSIGNMENT_MANAGE`
- `ANNOUNCEMENT_MANAGE`
- `ACADEMIC_OPS_MANAGE`
- `COURSE_MATERIAL_MANAGE`
- `ATTENDANCE_MANAGE`
- `LEAVE_REQUEST`
- `REPORTS_VIEW`
- `PORTAL_SETTINGS_MANAGE`

The `/api/teacher/**` request-level gate also accepts `PERM_ACADEMIC_OPS_MANAGE`,
so delegated academic staff can reach the controller-level checks instead of being
rejected by the coarse path filter. Course ownership checks still apply to course
and student records.

JWT-authenticated requests reload the current user details; disabled accounts are
not authenticated from previously issued access tokens. Deactivating an account
therefore blocks its existing tokens on the next request.

Refresh tokens are single-use: `/api/auth/refresh` atomically revokes the presented
token and returns a replacement. Replays of a consumed token are rejected, including
concurrent reuse attempts; integration tests cover sequential replay and concurrent
refresh requests.

Authentication rate limiting ignores `X-Forwarded-For` unless the socket peer is
listed in `AUTH_TRUSTED_PROXY_IPS` (comma-separated exact proxy IPs). For trusted
proxies, the limiter uses the final forwarded address, so the proxy must overwrite
the header or append the verified connecting client IP. Leaving the setting empty
is the secure default for direct deployments.

Access JWTs carry the user's credential version, which is checked against the
current account on every request. Password changes, password resets, and admin
password or role updates increment that version, invalidating existing access JWTs.
Deactivation and revoke-all-sessions also increment the version, so an access token
cannot become valid again after account restoration, and revoke-all immediately
blocks existing access tokens. Flyway migration `V24__user_credential_version.sql`

Profile privacy, notification, and quick-link preferences are loaded and saved
without overwriting each other's settings. The Profile page explicitly notes that
the current privacy selections do not alter institutional access rules, notification
delivery is controlled by configured services, and quick-link visibility is applied
to dashboard shortcuts. Unsupported language choices are disabled rather than
implying a translated interface exists. Dashboard quick links use the saved selection
for courses, assignments, campus events, and messages; assignment and event links open
their corresponding tabs. The former unsupported "Peer Forums" choice is now
"Messages," which points to the existing inbox. Flyway migration
`V24__user_credential_version.sql` adds the persisted field; older JWTs without the
claim are rejected after deployment and users must authenticate again.

Notification records now carry an explicit category (`ASSIGNMENT`, `GRADE`, `SYSTEM`,
or `PAYMENT`). Existing records default to `SYSTEM`; profile notification selections
filter the dashboard preview only and do not suppress notices from the full
notifications page or change email delivery. Existing notification producers still
create system notices for general events; approved grades and confirmed payments now
also generate category-specific notifications. New assignments notify students enrolled
in the associated course; repeated grade publication and payment confirmation do not
create duplicate notices.
Flyway migration
`V26__categorize_notifications.sql` adds the category field.

Password-reset secrets are SHA-256 hashed before persistence. A conditional update
atomically consumes each valid reset token, so concurrent reset requests cannot both
change the password with the same link. Outstanding reset links issued before this
change will no longer validate and must be requested again.
The reset endpoint enforces the same 8–100 character password length policy as
authenticated password changes and accepts the new password in a JSON request body,
not in the URL query string. Reset-token validation also uses a POST body, the reset
page captures and removes the token query in the document head before loading other
resources, and sends no referrer information.

User JSON responses omit credential-version, password-change, and normalized
identity internals. Password-reset, email-verification, and refresh-token entities
also suppress their stored token values/hashes from serialization. Request DTOs now
enforce the corresponding persisted field limits for chat, hostel applications,
assignment submissions, rejection reasons, system settings, and exam permit IDs.

Admin-provisioned student accounts require a password change before portal API
access. Their initial authenticated session has no role or permission authorities;
the JWT filter allows only password change, logout, login, and password/email
recovery endpoints until the account password is changed. The successful change
invalidates prior access and refresh credentials. Flyway migration
`V27__require_password_change_for_provisioned_users.sql` adds the persisted flag;
existing accounts are not forced through the new flow.

Public admissions status checks require both the application number and matching
email in a POST request body. Number and email mismatches produce the same error,
and the response omits applicant name and email.
Public admissions submissions and status checks share a per-client IP token
bucket (5 requests per minute by default); programme and academic-year option
reads are not limited. Like the existing authentication limiter, this is
in-memory and per application instance, so multi-instance deployments need a
shared rate-limit store for cluster-wide enforcement.

Admission submission normalizes email before persistence and enforces a unique
normalized email/academic-year key in the database, closing the concurrent
check-then-insert gap. Before V28 is applied to an existing database, operators
must review normalized email/year duplicates with the documented query and
resolve them without deleting records.

Email-verification secrets are also SHA-256 hashed at rest and atomically consumed
when confirming an address. Verification tokens are posted in request bodies after
the browser removes them from history; new email links carry the token in the URL
fragment, which browsers do not send in the initial HTTP request. The page sets a
`no-referrer` policy. Links issued before the new static-page URL format was
deployed may need to be requested again.

Email addresses for password-reset and resend requests are accepted in validated
JSON bodies rather than URL query strings, preventing those addresses from being
copied into access logs and URL history. New reset and verification emails link to
the actual `.html` static pages and place their tokens in fragments.
Password-reset and email-verification resend requests return the same response
for known and unknown addresses, preventing account enumeration through those
public endpoints.

The user's TOTP secret is excluded from JSON serialization, so returning a user
entity from an API cannot disclose the authenticator seed.

TOTP seeds are now encrypted at rest with AES-256-GCM using the externally managed
`MFA_ENCRYPTION_KEY` (Base64-encoded 32-byte key). The application fails startup if
MFA is enabled without a valid key; retain the same key across restarts and backups.
Existing plaintext seeds are accepted for authentication and encrypted after
successful use or setup. New TOTP seeds use RFC 4648 Base32. Migration V25 disables
accounts that have no complete valid seed, correcting the previous schema default
that marked un-enrolled users MFA-enabled. Student and staff login forms now prompt
for an authenticator code when the API signals that MFA is required. MFA setup and
verification now require the current password; disabling active MFA requires both
the current password and a valid TOTP code. Enabling or disabling MFA increments the
credential version and revokes refresh sessions, requiring the user to sign in again.
Migration V25 clears malformed legacy seeds and disables MFA for those accounts;
users with a cleared seed must enroll again.

Because CSRF tokens are not used, the application now rejects startup unless
`COOKIE_SAME_SITE` is `Strict`; deployments must not relax this setting without
implementing and testing explicit CSRF defenses.

Profile → Security now displays the refresh sessions returned by the authenticated
session API and supports revoking an individual refresh token or signing out
everywhere. Individual revocation prevents that session from refreshing; its
already-issued access token can remain usable until expiry. “Sign out everywhere”
also increments the credential version, immediately invalidating existing access
tokens.

## Important rule

Delegated grants must remain narrow. For example:

- `FINANCE_PAYMENT_CONFIRM` does not authorize refunding or reconciliation.
- `REPORTS_VIEW` does not grant portal settings or admin operations.
- `ASSIGNMENT_MANAGE` does not grant exam or portal-admin access.
- `ACADEMIC_OPS_MANAGE` does not grant procurement access.
- `PERM_*` assignments are checked server-side; they do not bypass ownership constraints.

## Validation

The focused security integration suite remains the source of truth for authorization boundaries. The current green run validates the delegated-permission behavior, including positive and negative flows across finance, reporting, admissions, procurement, HR, and library permissions.
