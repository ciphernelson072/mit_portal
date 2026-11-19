# Validation Results — 7 November 2026

## Scope

Validated the current application source and Flyway migrations V1–V49 using
disposable local databases. No live database credentials or `.env` files were
read. No production database, deployment, or data migration was used.

## Results

| Gate | Command or setup | Result |
|---|---|---|
| Java unit/integration suite | `.\mvnw.cmd -q test` | **PASS** — 394 tests, 0 failures, 0 errors, 0 skipped |
| Fresh MySQL migration | `.\mvnw.cmd -q -Pmysql-integration -Dtest=MySqlSchemaIntegrationTest test` | **PASS** — MySQL `8.0`, all 49 migrations applied through V49; Hibernate schema validation passed |
| Fresh TiDB migration | Local `pingcap/tidb:v8.5.0` with in-memory UniStore; start the application with Flyway enabled and Hibernate `ddl-auto=validate` | **PASS** — TiDB `8.0.11-TiDB-v8.5.0`, all 49 migrations applied through V49; application context and Hibernate schema validation passed |
| Current-source application package | `.\mvnw.cmd -q -DskipTests package` | **PASS** |
| Frontend unit tests | `npm run test:unit` | **PASS** — 12 tests |
| JavaScript validation | `npm run build` | **PASS** — 30 static JavaScript files |
| Browser end-to-end suite | `.\ops\run-e2e.ps1 -Port 18090` with isolated H2 | **PASS** — 88 tests, 1 worker |

The MySQL migration gate used Testcontainers. Both database tests used fresh,
disposable local schemas. The TiDB container and application test process were
removed/stopped after validation.

## Test harness correction

The first Playwright run had 87 passing tests and one timeout in the profile
preferences test. The test awaited a click that opened a blocking browser alert
before accepting that alert, deadlocking the test. The test now starts the click,
accepts the dialog, then awaits the click. The complete Playwright suite passed
on rerun. This correction changes only
[`tests/e2e/portal-regression.spec.js`](../tests/e2e/portal-regression.spec.js);
no application behavior was changed.

## Environment and limits

- Java `25.0.5.0.2`, Maven `3.9.9`, Node.js `26.7.0`, npm `12.0.2`, Docker
  `29.8.2`.
- The TiDB validation used single-node in-memory UniStore, not a distributed
  PD/TiKV cluster. It verifies migration and schema compatibility, not
  distributed-cluster behavior, durability, or performance.
- These results are local validation evidence only; they do not authorize or
  perform production rollout or data migration.

## Follow-up full-system debug pass

On the evening of 7 November, the Java test suite was rerun successfully
(394 tests, 0 failures, 0 errors, 0 skipped); `npm run test:unit` passed all
12 tests and `npm run build` again validated all 30 static JavaScript files.
The complete 88-test Playwright suite passed against the updated backend using
`.\ops\run-e2e.ps1 -Port 18090`.

This follow-up found a conflicting service-worker test expectation in
`tests/service-worker.test.js`: a newly present test expected first-time
navigation to cache an authenticated teacher dashboard, contradicting the
existing policy and test that private dashboards must not be precached.
The test now asserts that such a page is not cached. This was a test-only
correction; production service-worker behavior was not changed. The 12-test
unit suite and JavaScript syntax gate passed after the correction.

The code/doc review also confirmed product and deployment gaps remain rather
than local build failures: result/announcement email notification methods
are wired but require target inbox/bounce/retry verification; retake and CGPA
policy needs institutional decisions; payment provider acceptance, production
backup/restore, durable/shared file storage and multi-instance rate limiting
require external operating decisions or target-environment verification.

A preference mismatch was fixed after confirmation: the profile's unchecked
“Receive email notifications” option is now enforced for optional grade/CGPA
and announcement emails. Missing or malformed preferences fail closed. Security,
account, and admission emails are unaffected. Regression tests cover opted-out and opted-in behavior for both delivery paths
and the saved preference policy. Targeted tests and the full post-fix suite
passed.

No credentials were opened, and no production configuration or data was
changed. The browser debugger could not be used because its managed Chromium
executable was unavailable; the repository's Playwright CI browser suite
remains the browser evidence.

The first attempt to use the isolated E2E runner found a PowerShell issue:
`java -version` writes to stderr, which interacted with the script's
stop-on-error setting. The runner now temporarily uses continue-on-error while
capturing the version output and restores the original preference. The
corrected runner built the current JAR and completed all 88 browser tests.

## Login redirect regression fix — 8 November 2026

The login issue was traced to login pages redirecting to a protected dashboard
based only on a browser-local role hint. Role hints can remain after the
HttpOnly authentication cookies expire or are cleared. The staff and student
login forms now remain available until credentials are submitted; server-side
authentication and dashboard role enforcement are unchanged.

The service-worker cache was advanced from v22 to v23 so installed clients
discard stale cached assets when the updated worker installs. Browser regression
tests seed stale ADMIN and STUDENT role hints and assert that both login forms
remain visible. On 8 November, both targeted browser tests passed, the complete
isolated Playwright suite passed 90 tests, the Java suite passed 395 tests with
no failures/errors/skips, all 12 frontend unit tests passed, and JavaScript
syntax validation covered 30 files. The isolated browser runner packaged the
current JAR; inspection confirmed it contains the updated admin login, student
login script, and v23 service worker. Relative links across repository Markdown
files were also checked and resolved.

The local Docker daemon was unavailable, so the Docker image build and MySQL
Testcontainers gate were not rerun in this pass. Earlier disposable MySQL 8
and TiDB V1-V49 results remain recorded above; no migration or Java persistence
code changed for this login fix. The fixes must pass CI on the exact commit
connected to Render before deployment. Local-source verification does not mean
the Render service has been updated; verify the deployed release commit and
login pages after deployment. No production deployment or database was changed.
