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

## Logs and errors

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
