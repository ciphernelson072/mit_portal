# Release Checklist

Use [REMAINING_WORK_AND_DEPLOYMENT.md](./REMAINING_WORK_AND_DEPLOYMENT.md) as
the phased runbook for closing these items and deploying to Render. This
checklist records evidence; do not mark an item complete until verified in the
actual target environment.

## Verification records

### Current source verification (31 October 2026)

- [x] Final `mvn test` passed; 384 tests with 0 failures, 0 errors, and
  0 skipped (31 Oct 2026). MySQL/Testcontainers integration tests are excluded;
  this does not validate V44/V45 against a live or disposable MySQL/TiDB database.
- [x] `.\mvnw -q -DskipTests compile` completed successfully.
- [x] Focused permission, assessment-calculation, grade-approval, academic
  report persistence, security integration, and version-bound administrator
  review acknowledgment tests passed.
- [x] Current isolated Playwright run passed all 87 tests against a disposable
  H2 database and loopback-only server. This exercises UI behavior but does not
  establish MySQL/TiDB migration compatibility or live provider acceptance.
- [x] `.\ops\run-e2e.ps1 -Port 18089` passed all 87 isolated Playwright tests on
  31 October 2026 against the repository's disposable H2/sample-data server.
- [x] `npm run test:unit` passed all 10 frontend unit tests; static JavaScript
  files and updated browser specs passed `node --check`.
- [x] Flyway V1-V43 applied to a disposable MySQL 8.0 database; production
  application startup completed Hibernate schema validation. This was a
  manual loopback-only validation; the Testcontainers profile remains blocked
  by a Docker client/daemon API-version mismatch.
- [x] Flyway V1-V45 applied successfully to a fresh disposable MySQL 8.0
  container on 31 October 2026: all 45 migration records succeeded, the
  application reached its started state with Hibernate validation enabled,
  and the V44/V45 columns were present. This loopback-only evidence does not
  replace TiDB or production acceptance.
- [x] TiDB Cloud pilot applied and verified Flyway V43-V45 on 31 October 2026
  after a fresh `database-20261031-091347.zip` backup. The production-profile
  application started, `/actuator/health` returned HTTP 200, and the V44/V45
  database columns were present. Live correction-to-republication acceptance
  and institutional policy sign-off remain separate gates.
- [x] Administrator-authorized result-correction API and admin dashboard
  action implemented; backend tests and focused grade-review/correction
  Playwright tests passed. Target TiDB rollout and authenticated teacher
  correction-to-republication acceptance remain pending.
- [x] Administrator assessment review is now stored with reviewer, timestamp,
  and result-state fingerprint. Approval rejects unreviewed or changed result
  states; teacher/student role checks and complete role-based browser flows pass.
- [ ] The latest source-level test run is not a substitute for current browser,
  provider-sandbox, production-database, backup/restore, or live operational
  acceptance.
- [x] Validate V44 and V45 on disposable MySQL 8.0.
- [x] Validate V44 and V45 on the TiDB pilot after
  a fresh verified backup, then test the complete correction-to-reapproval,
  assessment-review, and publication path there; see
  [academic results workflow](./ACADEMIC_RESULTS_WORKFLOW.md).

- [x] 22 Oct 2026 pilot smoke check: production-profile app connected to TiDB
  with TLS identity verification; Flyway validated all 36 migrations and
  reported the `mit_student_portal` schema up to date. Local and temporary
  HTTPS tunnel health endpoints returned `UP`; the public landing page and
  admin/student login pages loaded. Direct unauthenticated requests to the
  student and teacher dashboards redirected to Access restricted. This did
  not exercise authenticated role workflows or external email delivery.
- [x] 22 Oct 2026 manual restart procedure verified: the current-user launcher
  created a fresh Quick Tunnel, updated only the ignored pilot origin settings,
  and started the app against the current TiDB schema. Local readiness and the
  public homepage/readiness endpoints returned `UP`/HTTP 200. The URL is
  written to `%LOCALAPPDATA%\MITPortal\portal-url.txt`; it changes on each
  Quick Tunnel restart.
- [x] `mvnw.cmd test -q` completed successfully in this workspace.
- [x] `npm run test:unit` - 10 passed.
- [x] `node --check` - all 28 static frontend JavaScript files passed.
- [x] `.\ops\run-e2e.ps1 -Port 18089` - all 79 isolated browser tests passed.
- [x] Temporary HTTPS test tunnel to the production-profile TiDB app; public
  home, admissions, student login, administrator login, and readiness routes
  returned HTTP 200. The retained `portaladmin` authenticated successfully.
- [x] TiDB reset verified: only the original admin remains; applicant,
  student, teacher, campus, course, and transactional data are empty.
  Pre-reset data-only snapshot SHA-256 verified in the private local backup
  directory.
- [x] Rotated the retained admin password to a temporary one-time credential
  and required a password change at next login.
- [x] Production-profile Windows logon task enabled for the requested test
  access. It runs after sign-in and uses a temporary, non-guaranteed Quick
  Tunnel; it is not a semester-ready deployment.
- [ ] Campus readiness is not established: no institution-controlled
  hostname, campus-network acceptance, restore rehearsal, or semester
  operations sign-off has been verified.
- [ ] Complete campus UAT, supported-browser/accessibility acceptance, and named
  support ownership. Chromium/Brave viewport evidence does not establish
  Firefox/Safari, assistive-technology, keyboard-only, managed-network, or
  low-bandwidth acceptance. See
  [Campus and support acceptance](./CAMPUS_AND_SUPPORT_ACCEPTANCE.md).

## Automated verification

- [x] Previous production Docker image build ran `mvn verify` with 219 backend tests passing at that time; the Docker build context includes `src/test`. Current workspace test totals are recorded below.
- [x] Fresh production-profile Docker startup applied Flyway migrations V1-V32 to MySQL 8.0, passed Hibernate schema validation, and returned `UP` from `/actuator/health`.
- [x] One-time initial administrator bootstrap was exercised against that fresh database and the created administrator completed an API login; test credentials and database were disposable.
- [x] `mvn test` - 238 tests, 0 failures, 0 errors (MySQL/Testcontainers test excluded; 17 Oct 2026).
- [x] MySQL/Testcontainers test attempted on 17 Oct 2026 but blocked before test execution: local Testcontainers configuration forces the Npipe strategy for missing `//./pipe/docker_engine`. No migration assertion ran; rerun in GitHub Actions or after correcting the local Docker/Testcontainers environment.
- [ ] Correct the Docker client API compatibility for the MySQL/Testcontainers
  profile; manual disposable MySQL 8.0 validation through V43 succeeded.
- [x] `npm install`
- [x] `npx playwright install` - browser available for local acceptance testing
- [x] `npm run test:e2e -- --workers=1` - 52 passed against a loopback-only server using a disposable in-memory H2 database and test-only configuration; no shared Laragon data was used.
- [x] `npm run test:e2e -- --workers=1` - 63 passed in Brave Browser (12 Oct 2026) against a loopback-only server using a disposable in-memory H2 database and test-only sample accounts. This includes admin, teacher, student, public-site, file-download, and responsive workflows; external email delivery and the shared MySQL database were not used.
- [x] `.\ops\run-e2e.ps1` - all 79 isolated browser tests passed (21 Oct 2026) against a disposable H2/sample-data server on a separate loopback port. This includes real UI-to-API message sending, verifies `/actuator/prometheus` requires authentication, covers admin course categories/Settings/academic reference selectors, and checks login/password-change form containment plus student/teacher/admin dashboard collisions across mobile, tablet, and desktop viewports, and confirms the current role dashboards and admissions/public-site coverage; it does not verify TiDB/MySQL workflows or external monitoring.
- [x] Manual Brave review of all admin dashboard sections and linked Messages, File Management, and Operations workspaces; all student dashboard module destinations; teacher dashboard and linked workspaces; and notification/profile/theme/message controls (13 Oct 2026). Cross-role attempts to open admin-only and teacher-only pages redirected to Access restricted. Empty-demo states were rendered for missing student programme/transport-pass data, and MFA controls stayed hidden while disabled; these are not production MySQL or SMTP acceptance evidence.
- [x] `npm run test:unit` - 10 frontend unit tests passed (17 Oct 2026); all static frontend JavaScript files also passed `node --check`.
- [ ] GitHub Actions run passes the Playwright E2E job against its isolated H2/sample-data server.
- [x] Focused public admissions browser test verifies application submission and explains administrator approval/enrollment before portal login creation (1 passed).
- [x] Admission submission and enrollment service tests passed (13 tests).
- [x] `mvn -DskipTests package` - deployable JAR built successfully.
- [x] Confirm the profile does not advertise MFA when MFA is disabled (browser regression verified).
- [ ] MFA enablement remains gated by policy and secret/key readiness. The
  deployment needs `MFA_ENCRYPTION_KEY`, encrypted key backup/recovery controls,
  and a tested loss-of-authenticator support procedure. Current support code has
  no recovery-code implementation; recovery codes are not enabled. See
  [MFA_RECOVERY.md](./MFA_RECOVERY.md).

## Production configuration

- [ ] For the local-Java/TiDB pilot, apply every Flyway migration to a new,
  disposable TiDB Cloud database with TLS identity verification, then exercise
  critical MySQL-specific queries and workflows. TiDB compatibility is not
  established by a successful MySQL test run.
- [x] Initial TiDB Starter smoke test: all 32 Flyway migrations applied with
  TLS identity verification on the fresh `mit_student_portal` database
  (14 Oct 2026). Broader MySQL-specific workflow acceptance, backup/restore,
  and operational checks remain open.
- [ ] Verify the named Cloudflare Tunnel hostname routes only to the
  loopback-bound Java service; confirm secure cookies, exact-origin CORS, and
  forwarded-client rate limits from external student devices.
- [ ] Record the TiDB Starter quota, database access-list owner/update process,
  backup/restore procedure, and response to quota exhaustion before inviting
  pilot users.
- [x] Production startup guardrails were strengthened in `ProductionConfigurationValidator` to fail fast on unsafe production values (JWT secret length, DB credentials, frontend HTTPS requirement, CORS allow-list validation, upload path validation, and secure-cookie enforcement).
- [x] Production startup now rejects non-HTTPS frontend/CORS origins, requires the canonical frontend origin in the CORS allow-list, and always requires secure cookies.
- [ ] `DB_URL` points to the production database.
- [ ] `DB_USER` is a dedicated non-root user.
- [ ] `DB_PASSWORD` is stored in a secret manager.
- [ ] `JWT_SECRET` is at least 32 random characters and unique to this deployment.
- [ ] `FRONTEND_URL` uses HTTPS.
- [ ] `CORS_ALLOWED_ORIGINS` contains only trusted HTTPS origins.
- [ ] `UPLOAD_DIR` is persistent and writable by the application.
- [ ] `COOKIE_SECURE=true`.
- [ ] `PAYMENT_WEBHOOK_SECRET` is a private random secret; Compose has no signing-key fallback.
- [ ] The application is reachable remotely only through a TLS-terminating reverse proxy.
- [ ] Demo/sample credentials are disabled or changed.
- [ ] MySQL port is not publicly exposed.
- [ ] Deployment is single-instance or a trusted gateway enforces shared login/admissions rate limits.

## Product and platform integrations

See the full UI/backend/provider inventory in [INTEGRATION_GAPS.md](./INTEGRATION_GAPS.md).

- [ ] Verify approved production email delivery and account lifecycle: application receipts contain no credentials, admission credentials are sent only after acceptance/enrollment, and provisioned student and teacher accounts require a password change before portal access. Teacher hiring now sends temporary sign-in details when mail delivery is configured and never returns credentials through the API response.
- [ ] Obtain approved privacy/retention policy sign-off and publish the privacy
  officer/contact, purpose, legal basis, retention schedule, legal holds,
  data-subject rights, deletion boundaries, and third-party inventory. The
  privacy page currently identifies this as an unapproved launch gap. See
  [Privacy, retention, hosting, and recovery gates](./PRIVACY_RETENTION_AND_RECOVERY.md).
- [ ] Flutterwave provider code is present, but keep live payments disabled until
  sandbox checkout, webhook/retry, settlement reconciliation, and target
  environment acceptance are complete. Keep other online channels disabled
  until their provider-specific flows are implemented and tested. Confirm the
  bank-transfer reconciliation operating procedure. Focused Flutterwave
  service/controller tests pass, including duplicate completed webhook
  handling, but live sandbox acceptance remains blocked until matching test
  credentials and HTTPS webhook configuration are supplied.
- [ ] Do not use legacy teacher grade update/delete endpoints to correct
  approved results until the controlled correction workflow is implemented and
  tested.
- [x] Added the Spring-managed Prometheus registry and exposed `/actuator/prometheus`; isolated browser acceptance verified anonymous access is rejected and an authenticated administrator can scrape metrics (14 Oct 2026).
- [ ] Configure monitoring-network scraping, central logs, error reporting, and
  tested alerts for the selected deployment. [Operations monitoring and recovery runbook](./OPERATIONS_MONITORING_RUNBOOK.md)
  defines the required target, alert, redaction, backup, and restore rehearsal
  evidence.
- [ ] Decide persistent/shared file storage and off-host scheduled backup
  integrations for the actual topology. Local database/upload scripts pass
  checksum and restore paths, but production scheduling, off-host copies,
  missed-backup alerting, and an actual restore rehearsal remain required.
- [ ] Add full-stack MySQL-backed tests for critical UI-to-controller/service/database flows; mocked Playwright API fixtures alone do not provide this evidence.
- [ ] Decide whether SSO, SMS, CAPTCHA/bot management, generative AI, LMS/HR/finance/SIS synchronization, and automated deployment are in launch scope; document approved exclusions or implementation evidence. Deployment automation also requires protected branches, migration/rollback gates, backup-before-migration, post-deploy smoke tests, and named rollback owners.

## Operational verification

- [x] Fresh Docker Compose startup succeeds from an empty database volume.
- [x] Database backup created with `ops/backup-mysql.ps1` and restored in a disposable database.
- [x] Upload directory archived with `ops/backup-uploads.ps1` and restored to a disposable path.
- [x] `BACKUP_STATUS_FILE` and `UPLOAD_BACKUP_STATUS_FILE` point to their respective success markers in the Compose app configuration.
- [x] Health endpoint responds successfully (`/actuator/health` returned `UP`).
- [x] Windows launcher now defaults to loopback, rejects `0.0.0.0`, refuses LAN binding unless the selected local IPv4 belongs to a Windows Private network profile and `DB_URL` targets a local `_test` database, and no longer runs direct root SQL or drops `sis_db`; LAN acceptance still requires the actual trusted-network browser test.
- [x] Exercise `ops/reset-wifi-test-data.ps1` end-to-end against a newly created disposable local `_test` database: all 30 Flyway migrations succeeded, exactly one active ADMIN remained, student and teacher tables were empty, and the bootstrap JVM exited; the verification-only schema and DB account were then removed. The isolated uploads path was absent before and after the test.
- [ ] Logs and alerts are connected.
- [ ] Metrics/Prometheus access is restricted to the monitoring network and alerts are exercised.
- [ ] Rollback procedure has been tested in the target production-like
  environment, including schema-compatible application rollback and a
  coordinated database/upload restore, measured recovery targets, health checks,
  and the approved data-loss window.
- [x] Flyway migrations are the schema strategy; existing production databases still require a reviewed baseline/adoption plan before deployment.
- [x] Flyway V31 persists official academic-report approvals; review the migration and deployment backup/adoption plan before applying it to an existing production database.
- [x] Flyway V32 adds a transactional, one-time initial-admin bootstrap claim; enable it only for a new empty database and disable/remove bootstrap credentials immediately afterward.
- [ ] Apply and validate V44 and V45 against a fresh disposable MySQL 8.0
  database and validate Hibernate mappings. TiDB V44/V45 application and review of a copy
  of any existing production schema remain pending.
- [x] Implement the supplied five-point mark bands, 50% pass threshold, retake
  flag, NP/PP rules, and degree/diploma classification in policy version
  `UG_5_POINT_V1`.
- [ ] Confirm policy publication/sign-off with the institute and define CGPA
  rounding, retake replacement, retake credit treatment, and attempt limits
  before treating CGPA involving retakes as final. Focused calculation tests
  now verify fail-closed retake handling and document the current provisional
  two-decimal HALF_UP calculation; these tests do not constitute policy approval.
- [ ] Existing databases have run `ops/migrate-course-catalog.sql` before enabling course categories/codes.

## Browser acceptance

- [x] Student, teacher, and administrator login API smoke tests.
- [ ] Current LAN presentation smoke test from a trusted Private Wi-Fi network (the prior `11.11.0.248` result is historical and must not be treated as current evidence; the Wi-Fi profile was Public at last check, so no LAN test was attempted).
- [x] Browser-validate teacher roster, grades, published assignments, course materials, and present/absent attendance approval (focused Playwright workflows use test fixtures, with teacher ownership also checked against the isolated H2 API).
- [x] Browser-validate admin user creation/status, student admission, teacher hiring, course creation, grade approval/rejection, fee creation/payment review, student request status/details, and admissions approval/enrollment (API fixtures are test-only; admissions acceptance explicitly precedes student login creation).
- [x] Public admissions desktop layout uses the available page width and has no horizontal overflow at 390px; focused browser regression passes.
- [x] Mobile layouts at 360px, 768px, and 1440px widths have no horizontal overflow.
- [x] No browser console errors or failed network requests in the automated Chromium page matrix.

## Evidence still required before final submission

- [ ] Verify production secrets, database access, proxy/TLS boundaries, private monitoring, persistent uploads, and sample-data disablement against the actual deployment environment.
- [ ] Publish `ready-project-2026-10-21` to
  `ciphernelson072/mit_portal` and connect the repository to Render. The
  local `origin` is configured, but GitHub authentication is required to push.
  The remote `main` has unrelated history; publish a separate branch and use a
  reviewed pull request rather than force-pushing.
- [ ] Verify V31 and any required course-catalog migration against the intended database after a reviewed backup and schema adoption plan.
- [ ] Verify Flyway V32 and the first-admin bootstrap against the intended Render database before importing production data.
- [ ] Ask users to re-approve any reports approved by the pre-V31 in-memory implementation; those old approvals cannot be recovered from the database.

- [x] Capture test-data screenshots under `docs/screenshots/` for student, teacher, admin, public admissions, and mobile views (isolated H2 demo data; admissions uses a test-only programme/year fixture).
- [ ] Record the LAN URL and host IPv4 address used for the presentation, along with confirmation that the Wi-Fi profile was Private.