# Deployment and Local Testing

This guide describes the supported local test paths and the production
configuration boundary. The root [README](../README.md) is the source of truth
for current application behavior, operational scripts, and feature setup.
For IntelliJ IDEA and Laragon, use [IDE_SETUP.md](./IDE_SETUP.md).
For UI/API/provider integration status and work still required, use the
[integration gaps inventory](./INTEGRATION_GAPS.md).

## Local Docker test

From the project root on Windows:

```powershell
.\run-sis.bat docker-up
```

The script creates a local `.env` from `.env.example` when needed, runs
preflight checks, starts the Docker Compose app and MySQL services, and waits
for the health endpoint. By default, both published ports are bound to
`127.0.0.1`; the Compose MySQL database and upload directory are for local
testing, not a production deployment. Stop the stack with:

```powershell
.\run-sis.bat docker-down
```

`.\run-sis.bat docker-reset` permanently removes the Compose database volume.
Use it only when that local test data can be discarded; the command asks for
confirmation. Do not point local testing at Laragon's shared `sis_db`.

The default `.env.example` values are placeholders or local-only settings.
Replace them before starting the stack; never reuse demo credentials or
development secrets for a shared environment. Do not commit `.env`.
Sample data is disabled by default and should only be enabled against a
disposable local database.

## IntelliJ IDEA and Laragon

Open the project root containing `pom.xml` in IntelliJ IDEA and follow
[IDE_SETUP.md](./IDE_SETUP.md) for JDK, Lombok, database, and test setup. The
browser must load the frontend from Spring Boot so its relative `/api` calls use
the same origin. Do not serve the project source tree through Laragon Apache.

For device testing, follow the LAN safety steps in `IDE_SETUP.md` and the
network guidance in the README. Bind only to the trusted Wi-Fi adapter's exact
IPv4 address, use an isolated test database, and only proceed when Windows
classifies that network as **Private**. Do not disable Windows Firewall, add a
router port-forward, or expose MySQL. When the active network is Public, keep
the app loopback-only and defer LAN acceptance testing.

## Production deployment requirements

Production values must be supplied by the deployment environment or a secret
manager, not committed to source files or placed in a built image. At minimum:

- Use a dedicated, least-privilege database account and a private database
  endpoint. Do not use the Laragon/root account.
- Set `DB_URL`, `DB_USER`, and `DB_PASSWORD` for the intended production
  database.
- Set unique, randomly generated `JWT_SECRET` and `PAYMENT_WEBHOOK_SECRET`
  values; keep them private and persistent across restarts.
- Use HTTPS for `FRONTEND_URL` and only trusted HTTPS origins in
  `CORS_ALLOWED_ORIGINS`.
- Set a persistent, writable `UPLOAD_DIR` and keep `COOKIE_SECURE=true`.
- Keep sample data disabled. Do not use the local demo accounts in any shared
  or production environment.
- Expose the application only through a TLS-terminating reverse proxy. Do not
  publish the MySQL port to the public network.
- Keep `SAMPLE_DATA_ENABLED=false` and `FLYWAY_BASELINE_ON_MIGRATE=false`.
  Production startup now refuses either unsafe setting.
- A new empty production database has no default administrator. Use the
  guarded one-time initial-admin bootstrap documented in
  [RENDER_DEPLOYMENT.md](./RENDER_DEPLOYMENT.md), then remove its credentials
  and disable it after the first successful startup.
- The built-in login/admissions rate limiter is in-memory and per instance.
  Use one application instance or enforce equivalent shared limits at a trusted
  edge gateway before scaling horizontally; do not treat per-instance limits as
  cluster-wide protection.

The production Spring profile validates required configuration and validates
the database schema rather than creating it with Hibernate. Flyway manages
schema migrations. A pre-existing database needs an explicit, reviewed
adoption/baseline plan; do not set baseline-on-migrate or run migration SQL
against it without reviewing its schema and taking a verified backup. Existing
installations that need course catalog fields must review and apply
[`ops/migrate-course-catalog.sql`](../ops/migrate-course-catalog.sql) to the
intended database before enabling those workflows.

Flyway V31 stores official academic-report approvals in the database so they
survive application restarts and are consistent across application instances.
For an existing installation, review the V31 change together with the database
adoption plan and backup before release; do not manually create the table.

The Compose file is useful for local integration testing; its bundled MySQL and
local bind-mounted storage do not by themselves provide production-grade
availability, secret management, TLS, monitoring, or off-site backups.
The project-specific integrations that are implemented, partial, or still
unwired are tracked in [INTEGRATION_GAPS.md](./INTEGRATION_GAPS.md); do not
enable an external-facing channel based only on its UI or configuration value.

## Monitoring and release gate

The production profile exposes Actuator health, metrics, and Prometheus metrics.
Make the metrics endpoint reachable only from the approved monitoring network
and protect it at the reverse proxy or monitoring boundary. Configure alerts for
application health failures, sustained server errors, database connectivity,
upload/storage exhaustion, and missed database or upload backups. Keep logs in
the deployment's access-controlled central logging service, retain enough
history for incident investigation, and do not log passwords, tokens, payment
secrets, or uploaded document contents.

Before each release, record the exact application artifact and migration
version, take and verify a coordinated database-and-upload backup, run the
automated backend and browser suites, and deploy first to a production-like
staging environment. A rollback is only valid when the prior artifact supports
the migrated schema; otherwise use the tested recovery plan and explicitly
accept the data-loss window before restoring a backup. Production credentials,
network exposure, monitoring, restore, and rollback still require verification
in the actual target environment.

## Backups and recovery

Use the scripts under `ops/` to create checksummed database and upload backups.
Keep backup artifacts outside the application upload directory, protect them
with access controls, and transfer copies to the deployment's approved
off-site storage. A success marker reports that a backup script completed; it
does not prove that a restore works. Restore into a disposable database and
path first, and verify the restored data and files before relying on a recovery
plan. The root README documents the script parameters and backup-status
configuration.

There is no universally safe automatic rollback across database migrations.
Before a release, retain the exact prior application artifact and a verified,
coordinated database-and-upload backup. Roll back the application only when
the previous version is compatible with the current schema. If data restoration
is necessary, coordinate downtime and restore database and uploads together
using a tested procedure; restoring old data can discard writes made since the
backup. Test rollback and recovery against the actual target environment before
production release.
