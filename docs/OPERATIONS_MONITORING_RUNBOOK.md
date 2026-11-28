# Operations Monitoring and Recovery Runbook

## Metrics

Production exposes Spring Health, metrics, and Prometheus endpoints through
`/actuator/*`. `/actuator/health` is public for hosting health checks.
Operational endpoints must remain reachable only to authenticated
administrators or an approved monitoring network.

Before production traffic:

1. Configure Prometheus or the selected hosted monitor to scrape
   `/actuator/prometheus` over TLS from an allowlisted network.
2. Set a scrape target timeout below the application's request timeout and
   verify that authentication failures, certificate errors, and absent samples
   generate alerts.
3. Exercise alerts for application down, database connectivity failure, elevated
   HTTP 5xx responses, restart loops, and backup freshness.
4. Confirm dashboards show request volume, latency, errors, JVM memory/threads,
   database pool usage, and application uptime.
5. Store alert recipients and escalation contacts outside the application.

The application also publishes non-sensitive counters through
`/actuator/prometheus`: HTTP bad requests, HTTP 5xx errors, authentication
failures, access denials, consumed MFA recovery codes, and backup-health alerts.
Counter values contain counts only; they do not include usernames, recovery
codes, passwords, request bodies, or provider payloads.

The rate-limit implementation remains intentionally single-instance. Production
startup fails unless `APP_INSTANCE_COUNT=1`, `RATE_LIMIT_STORE=memory`, and
`FILE_STORAGE_ADAPTER=filesystem`. This is a deployment guard, not distributed
rate limiting or shared storage. Add a shared store, gateway policy, persistent
object/shared storage, and cross-instance tests before enabling replicas.

## Logs and errors

Application console logs use a structured key/value line format and include
`X-Request-ID`, HTTP method, route, status, and duration for each request.
The request filter accepts only bounded safe request-ID characters, generating
a UUID for missing or unsafe values. Logs must not include HTTP bodies or query
strings; secrets, passwords, OTPs, recovery codes, and provider payloads must
remain outside application log statements. A centralized log shipper may
forward stdout/stderr to an approved provider, but provider selection, retention,
access control, and alerting still require institutional acceptance.

Application logs are not a substitute for centralized logging. Select a hosted
log/error service, route structured application output there, and test:

- authentication failure and session-revocation events;
- payment verification and webhook mismatch events;
- backup success/failure events;
- uncaught server errors with stack traces;
- secret, password, token, and credential redaction.

Set retention, access roles, export controls, and deletion procedures before
processing student records. Never send raw request bodies containing passwords,
recovery codes, payment secrets, or personal documents to external error
tracking.

## Backups and restore

Use these repository scripts:

- `ops/backup-tidb-dumpling.ps1` and `ops/run-tidb-backups.ps1` for TiDB.
- `ops/backup-mysql.ps1` and `ops/restore-mysql.ps1` for MySQL.
- `ops/backup-uploads.ps1` and `ops/restore-uploads.ps1` for uploads.
- `ops/rehearse-restores.ps1` for a controlled disposable MySQL and upload rehearsal.

The rehearsal script rejects existing database names, requires a disposable name
matching `*_restore_rehearsal_*`, restores uploads to a temporary directory, and
verifies Flyway history and application tables after a MySQL restore. Use
`-DropDisposableDatabaseAfterSuccess` only for explicitly authorized cleanup.

Each restore target must be disposable and the database restore must be
authorized. Run a restore rehearsal that verifies:

1. backup and checksum integrity;
2. Flyway history and application startup;
3. representative users, academic records, finance records, and uploads;
4. current database plus upload consistency;
5. measured recovery time and recovery-point objectives;
6. rollback criteria and responsible approvers.

Production schedules must run off-host, use encrypted credentials, publish
success/failure state, alert on missed or stale backups, and enforce retention.
Do not treat a script present on the application host as an off-host backup.

The admin dashboard verifies backup evidence only; it does not schedule or
create backups. Configure `BACKUP_STATUS_FILE` and, when uploads are stored
separately, `UPLOAD_BACKUP_STATUS_FILE` to point to provider-maintained status
markers. Each marker must contain `completedAt`, `artifact`, and `sha256`; the
artifact and its `.sha256` sidecar must be readable by the app process. Set
`BACKUP_MAX_AGE_HOURS` to the institution's freshness target. A missing marker
is reported as not configured, not as a successful backup.

A scheduled daily backup-health monitor runs at 08:45 in
`APP_AUTOMATION_TIME_ZONE`. It verifies database and upload marker evidence,
artifact checksums, future timestamps, and freshness. It sends one daily
in-app alert per unhealthy backup kind to active administrators or users with
the `PORTAL_SETTINGS_MANAGE` permission. Database job locking prevents duplicate
alerts across application instances. This monitor does not create archives,
upload backups off-host, or replace an external monitoring/alert service.

## Portal automations

When an accepted applicant is enrolled, when a term registration window opens,
after the Registrar updates a student's current programme semester, or when a
required course is added to a curriculum during open registration, the portal
attempts to register eligible active students in that programme and semester
for open terms. A successful student course registration, programme transfer,
lifecycle reactivation, or restored student account also retries assignment
for the student's active enrollment(s).
Restoring an instructor account or changing a course's instructor also retries
required-course assignment for active enrollments whose current semester
requires courses taught by that instructor.
The generic admin account-restore route performs the same role-aware retry for
student and instructor accounts.
Releasing the final active financial hold retries assignment; releasing one
hold while others remain does not. Creating a section during open registration
retries eligible students previously skipped because no section was available.
Dropping a course while registration is open retries that course's required
assignments for active enrollments in the same academic year and term; the
student who dropped it remains subject to registrar review for that course.
Approving a prerequisite grade also retries assignments for that student's
active enrollments in open terms. The portal uses the active instructor
already assigned to each course and eligible sections; it does not select
instructors or advance student semesters. Review the assignment summary and
resolve missing instructors/sections, capacity, financial holds, prior drops,
and other reported issues.

Student course-unit visibility is derived from the same registered course
enrollment used by course feeds: all units under a registered parent course are
returned to that student, and manually linked legacy unit enrollments remain
visible. Dropped registrations do not grant unit access. This is a read-time
view, not a second registration record, so staff should manage course
enrollment through the academic registration workflow rather than manually
duplicate unit enrollment.

The daily fee and grade reminders run at 09:00 and attendance-risk alerts at
09:30 in `APP_AUTOMATION_TIME_ZONE` (default `Africa/Nairobi`). The daily
workers coordinate through the database lock table created by the latest
Flyway migrations. Notification emails are queued for users who enabled email
notifications and retry after 1, 5, and 30 minutes following initial failure.
Review recent attempts and exhausted failures in the admin dashboard's Email
Delivery Activity panel. Failed deliveries remain visible there for
investigation.

Attendance below 75% generates a student warning; it does not automatically
block exam permits. Progression recommendations do not advance a student:
registrar review and approval remain required. Grade reminders use the
optional deadline configured on the academic term.

Finance-created student charges start as drafts. Finance staff must approve a
draft in the Finance module before it is visible to the student or included in
reminders and balances. Existing fee records remain active after migration.

## Admission document handling

Reviewers manage the active document requirements in Admissions Review.
Applicants must upload each active required file when submitting their
application; optional files may also be supplied. PDF and common image files
are accepted within the configured upload-size limit. Reviewers can download
uploaded files from the application queue. Missing documents do not
automatically accept or reject an application; the reviewer makes that
decision.
