# Remaining Work and Render Deployment Runbook

Last reviewed: 8 November 2026

This document consolidates the remaining release work and the steps for a
controlled Render test deployment. It is not evidence that Render, an external
database, email, or production operations have been provisioned. Use it with
the [Render deployment details](./RENDER_DEPLOYMENT.md), the
[release checklist](./RELEASE_CHECKLIST.md), and the
[integration inventory](./INTEGRATION_GAPS.md).
The role-by-role cross-workflow findings are recorded in
[ROLE_WORKFLOW_GAPS.md](./ROLE_WORKFLOW_GAPS.md).

## Current release status (8 November 2026)

The login redirect fix is present in the local source: stale browser role hints
no longer redirect the admin or student login pages, and the service-worker
cache version is v23. Browser regression tests cover both roles. These local
changes are not proof of a Render release. The current branch is
`fix/admin-login-offline-styles`; its working tree contains uncommitted changes.
The existing Render service was observed to run a prebuilt Docker Hub image,
not directly build a Git branch. CI now publishes a tested image to the
`latest` and commit-specific tags on `main` and the current fix branch, assuming
the documented Docker Hub secrets exist. The current Render image source is
pinned to a digest, so it must be changed to the mutable `latest` tag after the
publish workflow passes. GitHub CI for this exact source state and public login
acceptance are still pending. Do not assume a Git push will update the live
service until the image setting is updated. Complete the image-release setup in
[RENDER_DEPLOYMENT.md](./RENDER_DEPLOYMENT.md), then verify the deployed image
digest and login flows using the [release checklist](./RELEASE_CHECKLIST.md).

## Current status update (3 November 2026)

The Maven compiler, Docker build/runtime images, and GitHub Actions target Java
25. The current local environment has Java 21 only, so the Maven test run fails
when Java 21 tries to load Java 25 test classes; it must be rerun with JDK 25.
Standard npm automation exposes `npm run build` and `npm test`. On 3 November
2026, syntax validation passed for 30 static JavaScript files and all 10
frontend unit tests passed.

The npm and Docker tool checks also exposed environment restrictions in this
workspace: PowerShell script execution initially blocked npm until commands
were run through `cmd.exe`, and Docker reported that its daemon/configuration
was inaccessible. These checks require a restored Maven cache/network and an
accessible Docker daemon before deployment evidence can be completed.

The last recorded commit is
`9889867` (`Harden admission email response assertions`) from 31 October 2026.
The current worktree contains numerous modified files and untracked source,
tests, and migrations. It is not a clean release snapshot; review and include
the intended changes before publishing or deploying, and do not assume
untracked files are present in a built artifact.

The previously recorded 384-test Maven result remains valid only for the
verified 31 October source state. On 3 November, `mvnw.cmd -q test` ran but
failed because the available Java 21 runtime cannot load Java 25 test classes.
The current source therefore still needs a complete Maven test run under JDK
25. The browser review was also blocked: the browser runner has no installed
Chromium executable, and the integrated browser policy blocks localhost access.
No new Render deployment, external database provisioning, provider sandbox,
email delivery, payment, backup, restore, or campus-user acceptance evidence
was established on 3 November 2026.

## Source and documentation update (31 October 2026)

This source tree contains Flyway migrations V1-V49. The corrected V37-V42
sequence has now been applied successfully to the TiDB pilot after a verified
database backup. A separate disposable MySQL 8.0 database applied V1-V43, and
the application passed Hibernate schema validation. The 31 October full Maven suite passed 384 tests with zero failures, errors,
or skips (`.\mvnw.cmd test`). This source-level run does not replace provider
sandbox testing or establish production-environment acceptance.
Migration V45 persists administrator review acknowledgment against the exact
assessment/result fingerprint and requires the same fingerprint at approval.
The 31 October full backend suite passed 384 tests after reconciliation
reference hardening; all 87 isolated Playwright tests and all 10 frontend unit
tests passed in the latest recorded browser run.

The source includes course-specific assessment components, separate mark
records, weighted-mark calculation with missing marks distinguished from zero,
administrator-only result snapshot approval/publication, linked retake
attempts, administrator-authorized approved-grade corrections, and persisted
administrator review acknowledgment. V44 adds
the supplied five-point grade bands, pass/retake validation, core-course
standing, and degree/diploma award classification. V44 and V45 were validated
on disposable MySQL 8.0 and applied to the TiDB pilot. Retake replacement,
retake credit treatment, and CGPA rounding remain unspecified and are not
guessed. See the [academic results workflow](./ACADEMIC_RESULTS_WORKFLOW.md).

Flutterwave hosted-checkout and server-side verification code is present.
Live-provider readiness is not established until sandbox flows, webhook
registration/retries, settlement reconciliation, and environment-specific
acceptance are completed. See [payment configuration](./PAYMENT_CONFIGURATION.md).

## Latest local readiness check

Checked on 21 October 2026 after the requested fresh TiDB test setup:

- The TiDB database was reset transactionally after the app and tunnel were
  stopped. 112 rows were deleted across 73 application/setup tables. The
  original `portaladmin` is the only remaining user; students, teachers,
  applicants, courses, campuses, academic setup, and operational records are
  empty. Permission definitions, role mappings, Flyway history, and the
  completed initial-admin bootstrap claim were preserved.
- A current data-only TiDB snapshot was written to the private local backup
  directory at `%LOCALAPPDATA%\MITPortal\backups` and its SHA-256 sidecar was
  verified before deletion. The SQL snapshot contains sensitive data; it is
  not committed to the source repository.
- The retained `portaladmin` password was rotated to a one-time temporary
  credential and `password_change_required` was set. Public API login
  succeeded and returned `passwordChangeRequired=true`. The temporary
  credential was shared directly with the requester, not recorded in this
  document.
- The production-profile app is bound to `127.0.0.1:8080`, uses the configured
  TiDB Cloud database, and is published through a temporary Cloudflare Quick
  Tunnel. The current URL is stored privately at
  `%LOCALAPPDATA%\MITPortal\portal-url.txt`; it changes when the tunnel restarts.
- Public home, admissions information/form, student login, administrator
  login, and readiness routes returned HTTP 200. The scheduled task is enabled
  and running after Windows sign-in.
- No project upload files remained to clear. The full isolated Playwright run
  passed all 79 tests; backend and frontend unit checks also passed in this
  workspace.
- This remains a temporary test route, not a semester-ready production
  service. The Quick Tunnel has no uptime guarantee; institution-controlled
  hosting, campus-network acceptance, approved privacy/retention terms,
  support ownership, restore rehearsal, and semester operations sign-off
  remain unverified. Use only applicant data explicitly approved for testing.

## Pilot status update (26 October 2026)

The public test pilot is **online** at
<https://mins-applications-essentially-rouge.trycloudflare.com>. This is a
temporary Cloudflare Quick Tunnel URL and may change whenever the tunnel
restarts. The production-profile application is bound to `127.0.0.1:8080`;
the public route is provided by the tunnel, not by an inbound port-forward.
The scheduled task `MIT Student Portal Cloudflare TiDB` is enabled and running.

The latest restart verified local `/actuator/health` as `UP`, the public root
and public health routes as HTTP 200, and unauthenticated student grades,
profile, and academic-report APIs as HTTP 401. An unauthenticated request to
`/admin-dashboard.html` redirects to `/unauthorized.html` using an HTTPS
`Location`, confirming the forwarded-header setting is effective. The runner
successfully cleaned up the previous app/tunnel, removed the stale URL, and
published the new URL only after public readiness.

The same public checks were repeated after the Maven run: the root and health
routes returned HTTP 200, the protected student APIs returned HTTP 401, and
the administrator page returned an HTTPS redirect. One intervening Cloudflare
request returned HTTP 530; subsequent `curl.exe` and application-level checks
returned HTTP 200, so the pilot is currently reachable but still has no
availability guarantee.

Before database repair, a verified TiDB Dumpling snapshot was created at
`%LOCALAPPDATA%\MITPortal\backups\database-20261026-114513.zip`. Its SHA-256
is `1DB3E5DF1520298CD4D2E2B324B1719054F3CCAFD4C5DB5D806DA8A0BBE8A141`;
the archive checksum and critical table/history contents were verified. The
failed V37 Flyway history row was repaired only after that backup was
available, then V37-V42 completed successfully. TiDB now reports V42 as the
latest successful migration. Earlier incomplete `mysqldump` attempts are not
backups and must not be used for recovery.

The V37, V38, and V42 migrations separate column creation from indexes and
constraints that reference those columns, avoiding the TiDB multi-clause
`ALTER TABLE` failure. The complete V1-V42 sequence was also applied to a
disposable MySQL 8.0 database, and production-profile startup passed Hibernate
schema validation there. `server.forward-headers-strategy=framework` is
configured for HTTPS-aware redirects behind the tunnel.

The source includes V43 for administrator-authorized correction records and
prior-result snapshots, V44 for the five-point policy, and V45 for assessment
review acknowledgment. V1-V43 were applied successfully to a
disposable MySQL 8.0 database, followed by Hibernate schema validation. On 31
October 2026 the complete V1-V45 sequence was applied successfully to a
fresh disposable MySQL 8.0 container, with all migration records successful and
Hibernate schema validation passing. Migrations V46-V49 were added after that
validation and still require fresh-database execution verification. On 31
October 2026, a fresh verified
TiDB backup was completed and migrations V43-V45 were applied and verified
on the pilot. Live correction-to-republication acceptance and institutional
policy sign-off remain separate gates.

**Validation refresh (7 November 2026):** The current source passed all 394
Java tests, 12 frontend unit tests, JavaScript syntax validation for 30 files,
and 88 isolated Playwright tests. Fresh disposable MySQL 8.0 and local TiDB
v8.5.0 schemas each applied Flyway V1-V49 and passed Hibernate schema
validation. See the [validation results](./VALIDATION_RESULTS_2026-11-07.md).
The TiDB check used single-node in-memory UniStore; it does not replace
distributed-cluster or production-topology acceptance.

This is still a **temporary test pilot, not a production service**. The Quick
Tunnel has no uptime guarantee or stable institution-controlled hostname.
Continue to use only data explicitly approved for testing. Institutional
hosting/privacy/retention approvals, encrypted off-host backups and restore
rehearsal, official grading/retake policy decisions, approval of the
correction/appeal procedure, support ownership, and Flutterwave
sandbox/reconciliation acceptance
remain release gates.

## Historical verification state (through 21 October 2026)

The following entries preserve dated checks and environment limitations. They
are historical evidence and are superseded where they conflict with the
26 October pilot update above.

- On 19 October 2026, the failed TiDB migrations were diagnosed and recovered
  without recreating the database. V34 and V36 now use ordered, separate
  `ALTER TABLE` statements because TiDB rejected adding a column and
  referencing it in the same multi-clause `ALTER`. Flyway validated and applied
  V34-V36; the live schema is at V36.
- Before each repair, a local SQL backup was created and checked for the
  Flyway history and affected tables, with a matching SHA-256 sidecar and
  restricted local permissions. The upload backup also completed. The dump
  was made while the portal was offline; the Docker-based Dumpling backup
  could not run because Docker Desktop's Linux engine was unavailable.
- The production-profile `MIT Student Portal Cloudflare TiDB` Windows logon
  task is enabled for the requested TiDB test. It uses an account-less
  Cloudflare Quick Tunnel and starts only after Windows sign-in. This is not an
  institution-controlled persistent hostname.
- The separate H2-only demo tunnel was stopped; the current public route is
  the TiDB-backed testing app.
- `ops\run-local.ps1` falls back to an in-memory H2 schema when no MySQL server
  is available; Flyway and outbound mail are disabled in that fallback mode.
  This fallback is only for local demo/testing, not a substitute for the
  production database or migration verification.
- At the time of this 21 October 2026 pilot check, the source contained
  migrations V1-V36. The successful MySQL 8 validation covered only V1-V32
  and predates V33-V36; it does not establish MySQL 8 compatibility for V33-V36.
  V34-V36 execution was verified against TiDB Cloud's MySQL-compatible endpoint.
  Migrations V37-V42 were added later; their database compatibility is not
  established by that historical pilot evidence. V1-V43 have since been
  validated on disposable MySQL 8.0 as described above.
- On 17 October 2026, `mvn test` passed 238 backend tests; this standard run
  excludes the MySQL/Testcontainers profile.
- On 17 October 2026, `npm run test:unit` passed 10 tests, all static frontend
  JavaScript files passed `node --check`, and `.\ops\run-e2e.ps1` passed all 74
  browser tests against a loopback-only server using disposable H2 data.
- The browser suite includes regressions for server-driven course categories,
  Settings navigation to real academic/finance records, and searchable academic
  references for timetable, examination, and result workflows. It also checks
  login/password-change form containment and student, teacher, and admin
  dashboard collisions across phone, tablet, and desktop breakpoints.
- The admin exam-list response generic typing was corrected; backend tests and
  the complete browser suite pass with the new protected endpoint.
- The MySQL/Testcontainers test was attempted locally but could not start:
  this machine's Testcontainers configuration forces a Windows named-pipe
  strategy for `//./pipe/docker_engine`, and that pipe is absent. This is an
  environment limitation; the migration test did not reach the application.
- A fresh MySQL 8 database previously applied Flyway V1-V32, passed schema
  validation, and the production-profile app returned `UP` from
  `/actuator/health`.
- The guarded first-administrator bootstrap was exercised against that
  disposable database and the new account completed API login.
- These checks do not verify the user's future Render account, hosted MySQL,
  DNS, secrets, SMTP provider, external backup process, or live user workflows.

## Phased remaining work

### Phase 0 - Prepare the source and external prerequisites

Complete before creating the Render Blueprint:

- [ ] Publish the prepared `ready-project-2026-10-21` branch to the existing
  GitHub repository `ciphernelson072/mit_portal` and connect it to Render.
  The local Git repository and `origin` are configured, but this workspace
  cannot authenticate the GitHub push. The existing public `main` branch has
  unrelated history; publish a separate branch and review/merge it with a pull
  request. Do not force-push or overwrite `main`.
- [ ] Provision a reachable MySQL-compatible database. Render's managed
  relational database is PostgreSQL and is not compatible with this app's
  MySQL JDBC driver and MySQL-specific Flyway migrations.
- [ ] Create a dedicated, least-privilege, non-root database user. Keep the
  database endpoint private where possible, require provider-supported TLS,
  and allow only the Render service's required network access.
- [ ] Decide whether this is a genuinely new empty database or an existing
  installation. For an existing database, stop and prepare a reviewed schema
  adoption/migration plan and verified backup; do not turn on Flyway
  auto-baselining as a shortcut.
- [ ] Decide whether the test deployment may incur Render service, persistent
  disk, external MySQL, outbound email, and backup-provider charges. The
  Blueprint specifies a paid `starter` web service and a 1 GB persistent disk.
- [ ] Identify a deployment owner and an administrator who can receive the
  initial account details and perform the acceptance checks.

Do not use real student, finance, or identity data during setup and smoke
testing.

### Campus user pilot gate

The project is not currently cleared for real campus users. The local browser
suite uses disposable H2 data and mocked API responses in several workflows;
it does not establish that the target database, campus network, mail provider,
or real user permissions work. Use this order before inviting even a small
campus pilot:

1. Rerun the isolated browser suite after any regression and record the source
   revision, test count, and result; do not use the
   suite's H2 database for campus user accounts.
2. Name the institutional test owner, campus IT/network contact, data/privacy
   approver, and support contact. Get written approval for pilot scope, dates,
   user roles, any costs, and the test data to be used.
3. Provision a separate, disposable test database and least-privilege
   application user. Apply the complete migration set to that database and
   verify the schema with the target database product/version. Never point
   campus testing at a production or shared student database.
4. Choose an institution-controlled HTTPS hostname and TLS termination
   method. Do not ask users to submit credentials over plain HTTP or use a
   temporary Quick Tunnel as a persistent campus service. Keep the application
   bound to loopback behind the approved ingress; do not expose database ports.
5. Configure exact HTTPS `FRONTEND_URL` and `CORS_ALLOWED_ORIGINS`, secure
   cookies, safe proxy/rate-limit settings, disabled sample data, persistent
   upload storage, and secret-managed credentials. Confirm the firewall and
   campus Wi-Fi profile/segmentation with campus IT before permitting access.
6. Create named test accounts for admin, teacher, and student roles using
   synthetic records only. Verify account recovery and support ownership;
   disable bootstrap credentials after the first admin is provisioned.
7. Rehearse database plus upload backup and restore. Confirm backup owner,
   encryption, retention, capacity alerts, and restore authorization before
   collecting any pilot submissions or files.
8. Run the target-environment acceptance matrix in Phase 2 from a staff
   workstation and at least one campus student device. Verify both permitted
   and denied cross-role/ownership actions. Keep logs and screenshots free of
   secrets and personal records.
9. Obtain sign-off from the institutional pilot owner after recording every
   accepted issue and unresolved failure. Only then decide whether to broaden
   the pilot or authorize real data under institute policy.

#### Campus acceptance matrix

Use synthetic names, records, documents, and inboxes controlled by the pilot
team. Record pass/fail, test role, browser/device, time, and a sanitized
reference to any defect; do not record passwords, tokens, personal records,
or uploaded document contents.

| Role / area | Verify allowed behavior | Verify denied or recovery behavior |
|---|---|---|
| Student | Sign in; review own profile, courses, attendance, grades, fees, messages, and requests; submit an approved test request/document; use supported mobile navigation. | Cannot view another student's records/files or access staff/admin pages; sign out invalidates the session; expired/invalid sessions return to sign-in without losing unrelated data. |
| Teacher | Sign in; view assigned courses/rosters; record attendance and submit grades/materials using test records; verify approval states. | Cannot view or change unassigned classes or student records; cannot access administrator-only functions; failed upload/validation shows an actionable error and does not report success. |
| Administrator | Sign in; configure test academic directory; create/deactivate test accounts; review admissions and requests; inspect audit-relevant state and exports. | Non-admin sessions cannot invoke admin APIs directly; invalid form data is rejected; disabled payment channels cannot accept card/payment data; bootstrap is disabled after initial setup. |
| Shared / operations | Confirm HTTPS certificate, exact-origin behavior, secure auth cookies, health/readiness, upload persistence after app restart, backup freshness, and monitored disk/database capacity. | Untrusted origins and unauthenticated protected endpoints are rejected; restart/restore behavior is known; alerts and support escalation reach their named owners. |
| Email and finance | Use only approved test inboxes and provider sandboxes. Confirm receipt/reset/enrollment messages contain only the intended test-account data. | Do not use a real payment, send a permanent password, or enable a provider whose signed callback and reconciliation path has not been accepted. |

Stop the pilot and notify its owner if there is cross-user data exposure,
unauthorized role access, unexplained data loss, secrets in logs, insecure
transport, failed restore, or any unexpected real payment/email. Preserve only
sanitized diagnostics and follow the institution's incident process.

If the institution explicitly authorizes a short, synthetic-data LAN demo
before HTTPS is available, isolate it to a trusted Private Wi-Fi network,
allowlist the exact host IP, use only the disposable `_test` database, disable
external links and email/payments, and shut it down after the session. Such a
demo is **not** an acceptable substitute for a campus-user pilot over HTTPS.

### Phase 1 - Configure and deploy the Render Blueprint

1. In Render, create a Blueprint from the connected repository and review
   `render.yaml` before applying it. Confirm the service is Docker-based, uses
   the production Spring profile, binds to `0.0.0.0`, uses Render's `PORT`, and
   has `/actuator/health` as its health check.
2. Enter the database connection values in Render's environment configuration:
   - `DB_URL`: JDBC MySQL URL for the provisioned database, including the TLS
     and timezone options required by that provider.
   - `DB_USER`: dedicated non-root application user.
   - `DB_PASSWORD`: that user's secret, stored only in Render's secret
     environment; never commit or paste it into source files.
3. Set `FRONTEND_URL` to the exact HTTPS origin Render assigns, with no path
   or trailing slash. Set `CORS_ALLOWED_ORIGINS` to the same trusted origin
   (or a comma-separated list of explicitly approved HTTPS origins). These
   values must agree or production startup validation will fail.
4. Keep the generated `JWT_SECRET` and `PAYMENT_WEBHOOK_SECRET` private and
   stable. Do not rotate either casually: changing signing secrets can
   invalidate active sessions or disrupt signed payment callbacks.
5. Keep the production safety settings enabled:
   `COOKIE_SECURE=true`, `SAMPLE_DATA_ENABLED=false`,
   `FLYWAY_BASELINE_ON_MIGRATE=false`, and `MFA_ENABLED=false` unless MFA has
   a separately approved, tested rollout and recovery plan. Preserve the
   Blueprint's proxy setting only behind Render's trusted ingress; do not
   copy forwarded-header trust to a directly reachable server.
6. For a new empty database only, perform the one-time administrator setup:
   temporarily set `INITIAL_ADMIN_BOOTSTRAP_ENABLED=true`,
   `INITIAL_ADMIN_USERNAME`, `INITIAL_ADMIN_EMAIL`, and
   `INITIAL_ADMIN_PASSWORD`. Use a unique username and password; the password
   must be at least 12 characters and no more than 72 UTF-8 bytes. The
   bootstrap is guarded by an atomic database claim and will not seed a
   non-empty users table.
7. Start the deploy and wait for the Render health check to pass. Inspect the
   build and application logs for migration or configuration errors without
   copying secrets or personal data into tickets or chat.
8. After the first administrator can sign in, set
   `INITIAL_ADMIN_BOOTSTRAP_ENABLED=false` and remove the bootstrap username,
   email, and password values from Render. Store the administrator credential
   securely and rotate it according to institutional policy.

The initial deployment applies all bundled Flyway migrations (currently
V1-V49) to an empty database. Validate the complete migration set against the
selected database engine before deployment. Do not enable bootstrap against a
populated database or treat it as an account recovery mechanism.

### Phase 2 - Verify the deployed test instance

Use test accounts and synthetic data. Record date, build/revision, migration
version, outcome, and the operator; do not record passwords, tokens, or
student records.

- [ ] Confirm `/actuator/health` reports `UP` after deployment and after a
  service restart.
- [ ] Confirm database connectivity and the expected schema/migration version
  using the approved operator interface. Check the service-status view if it
  is enabled; do not expose diagnostic endpoints publicly to make this check
  easier.
- [ ] Sign in as the newly provisioned administrator; sign out and confirm
  the session is invalidated.
- [ ] Create/use test student and teacher accounts. Verify allowed workflows
  and deny cases: students must not access another student's records, teachers
  must be limited to their assigned classes, and non-admin roles must not
  reach administrator functions.
- [ ] Exercise one representative read/write workflow, then upload and
  download an allowed test file. Restart the service and confirm the uploaded
  file remains available on `/var/data`.
- [ ] Confirm CORS behavior from the approved HTTPS origin, secure-cookie
  behavior, HTTPS redirects/TLS, and that MySQL is not publicly exposed.
- [ ] Confirm logs contain enough operational detail but no passwords,
  authorization tokens, payment secrets, or uploaded file contents.
- [ ] If SMTP is approved and configured, send a test email and verify receipt,
  sender identity, TLS, and provider delivery status. Email remains disabled
  by default in the Blueprint.
- [ ] Capture browser console/network errors and document each accepted or
  unresolved failure. Do not treat mocked Playwright API fixtures as proof
  that the live MySQL-backed deployment works.

Do not import real institutional data until the real-data gates below are
completed and approved.

### Phase 3 - Complete real-data and operational gates

#### Database migrations and recovery

- [ ] Take a verified backup before any migration to an existing database.
- [ ] Review Flyway V31 and V32 against the target schema and the institution's
  database adoption plan. V31 makes report approvals persistent; approvals
  created by the earlier in-memory behavior cannot be recovered, so ask users
  to re-approve those reports where applicable.
- [ ] For an existing installation that needs the course category/code fields,
  review and run `ops/migrate-course-catalog.sql` against the intended
  database before enabling those workflows.
- [ ] Rehearse a coordinated database and uploads restore. The Render disk
  stores uploads only and its snapshots are not a backup of the external
  MySQL database.
- [ ] Define backup encryption, off-host destination, retention, access
  controls, schedule, recovery point/recovery time objectives, and alert owner.
  For the in-app backup status to become configured, the external successful
  backup process must produce its marker, SQL artifact, and matching SHA-256
  sidecar at paths visible to the app. Never create a placeholder marker to
  make the status green.
- [ ] Test a restore into an isolated database and upload path, validate
  representative records/files, and document who can authorize recovery.

#### Monitoring and release operations

- [ ] Configure approved-network scraping for the implemented, authenticated
  `/actuator/prometheus` endpoint; restrict access to the monitoring network.
- [ ] Connect central logs and error reporting with retention, role-based
  access, PII/secret redaction, and an incident owner.
- [ ] Configure and exercise alerts for failed health checks, application
  errors, database connectivity, disk capacity, and missing/stale backups.
- [ ] Define who approves releases, how artifacts and migration versions are
  recorded, and how deployments are paused when a check fails.
- [ ] Test rollback/roll-forward. A previous application image is not a safe
  rollback if it cannot operate with the already-migrated schema. Prefer a
  tested forward fix unless a compatible restore plan is approved.
- [ ] Re-run the MySQL/Testcontainers migration test through V36 when the
  Windows Testcontainers endpoint issue is resolved. A prior run of the
  broader suite does not verify this migration through Testcontainers.
- [ ] Confirm the GitHub Actions Playwright job passes. Its H2/sample-data
  workflows complement, but do not replace, the production-profile MySQL
  smoke test or real-deployment acceptance.
- [ ] Add MySQL-backed end-to-end coverage for critical real routes: login,
  admission-to-account provisioning, payment attempt/review, report
  approval/download, file upload/download, role/ownership enforcement, and
  notification delivery.

#### Scope and scale decisions

- [ ] Keep the Render test service to one instance unless rate limiting is
  moved to a shared store or a trusted gateway. `RateLimitFilter` keeps state
  in process memory, so its limits are not shared across instances.
- [ ] Plan file storage before scaling. Current uploads use a filesystem;
  Render's attached disk is single-instance and prevents horizontal scaling
  and zero-downtime deploys. Evaluate object storage or supported shared
  storage before multi-instance operation.
- [ ] Approve or explicitly exclude each optional integration in
  [INTEGRATION_GAPS.md](./INTEGRATION_GAPS.md): campus SSO, SMS, CAPTCHA/bot
  management, generative AI, LMS/HR/finance/SIS synchronization, and automated
  deployment.
- [ ] Complete a current LAN presentation smoke test only if needed. Use a
  trusted Windows Private Wi-Fi network, exact host Wi-Fi IPv4 binding, and an
  isolated `_test` database. Do not expose the app or MySQL on a Public network.

## Integration readiness summary

The following items are not made operational merely by deploying the app:

| Area | Current state | Required before enabling |
|---|---|---|
| Email transport | SMTP and Resend transports are implemented; local SMTP has been configured and tested, but institutional production approval/delivery monitoring remains. | Confirm approved sender/domain, SPF/DKIM/DMARC, delivery/bounce monitoring, and end-to-end tests in the production environment. |
| Admissions/hiring email | Application receipts, post-enrollment student credentials, and hiring onboarding email are implemented. Student and teacher provisioned accounts require password change. Unrelated grade/announcement notifications and bounce/retry monitoring remain unwired. | Verify production email delivery and forced first-login password change; implement approved bounce/retry monitoring and event-triggered notifications with tests. Never email permanent passwords. |
| Grade/CGPA/announcement email | Notification methods have no verified application event callers. | Choose notifications, add user preferences/consent and event triggers, and test retries/duplicates. |
| Online payments | Generic payment records/callback path exist; provider-specific checkout and signature integration are not established. | Provider sandbox, documented signature validation, amount/currency/reference binding, replay/idempotency controls, refunds and reconciliation tests. Keep unavailable until then. |
| Bank transfers | Manual reference/reconciliation path exists. | Document an authorized reconciliation operating procedure or integrate a bank statement source with audit and discrepancy handling. |
| Prometheus | Spring-managed registry and authenticated `/actuator/prometheus` endpoint are implemented and browser-verified; external monitoring is not configured. | Configure approved-network scraping, dashboards, access controls, and exercised alert delivery. |
| MFA | Application TOTP flow exists; disabled by default. | Policy decision, secure 32-byte Base64 encryption key, recovery/support process, and full enrollment/login/revocation tests. |
| Logs/errors | No central export or error-tracking integration is verified. | Select a provider, redact secrets/PII, apply access/retention controls, and test alerting. |
| Backups | Local backup scripts exist; Render/external production schedule and restore are not set up. | Encrypted off-host database and upload backups, monitoring, retention, and restore rehearsal. |
| File storage | Configured filesystem storage; no object/shared-storage adapter found. | Persistent disk for one instance or implement/test shared object storage before scaling. |

Detailed frontend/backend/provider evidence and the optional integration
decisions are maintained in [INTEGRATION_GAPS.md](./INTEGRATION_GAPS.md).

## Stop/go decision

The Render Blueprint is suitable for a controlled test deployment after
Phase 0 is complete. It is **not ready for real institutional data** until the
database adoption and backup/restore plans, production secrets and access
boundaries, operational monitoring, file durability, and required integrations
have been verified in the actual target environment and the release owner has
approved the evidence.
