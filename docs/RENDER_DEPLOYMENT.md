# Render deployment for testing

The repository includes a root-level `render.yaml` Blueprint for a single
Docker web service. In Render, create a Blueprint from this repository and
provide the prompted `sync: false` environment variables. Do not deploy the
production profile until the database and public URLs are configured.
The current workspace has no Git repository or remote configured, so push this
source to a Git provider and connect it to Render before creating the Blueprint.
For a copyable environment-variable starting point, use the root
`.env.render.example` with Render's **Add from .env** control for the persistent
disk configuration. For a disposable Free-tier test, use `.env.render.free`.
Replace every `REPLACE_WITH_...` value first; do not use the local `.env`. Generate
`JWT_SECRET` and `PAYMENT_WEBHOOK_SECRET` directly in Render and replace the
placeholder values. The Free-tier file uses Resend's HTTPS email API (port 443),
so create a Resend account, verify a sender address/domain, create an API key,
and configure `MAIL_API_KEY` and `MAIL_FROM_EMAIL`. The application uses Resend
for verification, password reset, notification, and admin test emails when
`MAIL_PROVIDER=resend`; it sends plain-text message bodies. The SMTP-specific
Actuator mail health check is disabled for this API mode, while the admin system
status reports provider configuration and the time of its latest accepted test
email. The Free-tier file writes uploads to ephemeral `/tmp`; files can be lost
on restart, redeploy, or spin-down and must not be treated as durable.
For a phased list of all remaining work, post-deploy acceptance, and real-data
gates, follow [REMAINING_WORK_AND_DEPLOYMENT.md](./REMAINING_WORK_AND_DEPLOYMENT.md).

## Required MySQL database

This application currently requires MySQL: it uses the MySQL JDBC driver and
MySQL-specific Flyway migrations. Render's managed relational database is
PostgreSQL, which is not compatible with this application. Create or select a
separate, reachable MySQL-compatible database before deploying. Set:

- `DB_URL` to its JDBC URL, for example
  `jdbc:mysql://HOST:3306/DATABASE?useSSL=true&requireSSL=true&serverTimezone=UTC`
  (use the database provider's required TLS parameters).
- `DB_USER` and `DB_PASSWORD` to a dedicated, least-privilege application
  account. Do not use the database root account.

Allow the Render service to reach that database. If the provider uses network
allowlists, configure them using the current outbound IP information shown by
Render for this service. Back up the database at the database provider or with
a separate scheduled job, and verify restoration before using real student
data. The included persistent disk is for uploads; it is not a database backup.

## Required first-deploy URLs and secrets

Set `FRONTEND_URL` to the exact HTTPS origin Render assigns to this service,
such as `https://mit-student-portal.onrender.com`. Set
`CORS_ALLOWED_ORIGINS` to the same origin, with no path or trailing slash.
The production validator requires both values to be valid HTTPS origins and
requires the CORS list to contain the frontend origin. If Render assigns a
different hostname, update both environment variables before the first
successful deploy.

Render generates `JWT_SECRET` and `PAYMENT_WEBHOOK_SECRET` through the
Blueprint. Keep those values private and stable after deployment. The
production profile rejects placeholder or short values, root database users,
sample data, and Flyway auto-baselining.

## First administrator on a new database

An empty production database has no demo or default accounts. To create the
first administrator, set these service environment variables in Render for one
deployment:

- `INITIAL_ADMIN_BOOTSTRAP_ENABLED=true`
- `INITIAL_ADMIN_USERNAME` to a unique 3-64 character username.
- `INITIAL_ADMIN_EMAIL` to the administrator's reachable email.
- `INITIAL_ADMIN_PASSWORD` to a unique password of at least 12 characters.

The application creates the account only if the one-time database bootstrap
claim is unused and the users table is empty. The claim and account are saved
in the same transaction; a database that already contains accounts is never
seeded. After the first successful startup, remove the username, email, and
password variables and set `INITIAL_ADMIN_BOOTSTRAP_ENABLED=false`. The
database claim prevents the initial-account operation from running again even
if the variables are accidentally restored later. Never enable sample/demo
accounts in production.

## Persistent uploads and deployment behavior

The Blueprint uses a paid `starter` web instance with a 1 GB persistent disk
mounted at `/var/data`; uploads are stored under `/var/data/uploads`. This
keeps uploaded files across restarts and deploys. Render disks are only
available on paid services, are attached to one instance, and prevent
zero-downtime deploys and horizontal scaling. Increase the disk size if uploads
grow, and plan a move to object storage before scaling the application.

The production profile binds to `0.0.0.0` and uses Render's injected `PORT`.
The health check uses `/actuator/health`. Docker builds run Maven `verify`
before producing the runtime image, so a failed backend test blocks deployment.
The Blueprint enables forwarded client-IP handling for the auth rate limiter
because Render web services receive public traffic through Render's proxy and
are not directly reachable from the public internet. The filter uses the final
valid IP in `X-Forwarded-For`; do not enable this setting for a server that can
also be reached directly by untrusted clients.

Local launches bound to loopback trust only loopback proxy peers by default,
which allows the Playwright suite to use distinct forwarded test-client
addresses. Other local bind addresses keep forwarded headers untrusted unless
`AUTH_TRUSTED_PROXY_IPS` is explicitly set.

## Email and backup status

Email is disabled in the Blueprint until a provider is configured. For SMTP,
set `MAIL_PROVIDER=smtp`, `MAIL_ENABLED=true`, `MAIL_HOST`, `MAIL_USERNAME`, and
`MAIL_PASSWORD`; keep STARTTLS enabled and use a port allowed by your Render
plan. Render Free blocks outbound SMTP ports 25, 465, and 587. For Render Free,
the application also supports Resend over HTTPS: set `MAIL_PROVIDER=resend`,
`MAIL_ENABLED=true`, `MAIL_API_KEY`, and `MAIL_FROM_EMAIL` to a verified Resend
sender. Keep the API key in Render's secret environment values and never in
`render.yaml`. Confirm delivery with an administrator test email.

Database backup status will remain `Not configured` until an external,
successful backup process writes a marker, SQL artifact, and matching SHA-256
sidecar at paths visible to the app. The Render uploads disk and its daily
snapshots do not back up the external MySQL database. Do not add a placeholder
marker to make the status green.

## Testing deployment gates

Before using the deployment with real data:

1. Confirm the external MySQL database is empty or has been reviewed for a
   compatible migration path. New installations apply Flyway migrations from
   V1 onward; `FLYWAY_BASELINE_ON_MIGRATE` must stay `false` in production.
2. Deploy and wait for `/actuator/health` to report healthy.
3. Confirm the system-status API reports database connectivity and perform an
   administrator login, student login, role-boundary checks, and one upload and
   download using test accounts/data.
4. Configure SMTP and test a real message if outbound email is in scope.
5. Establish database and upload backup/restore procedures before importing
   real student, finance, or identity data.

This Blueprint is for a controlled test deployment, not a claim that the
external MySQL database, SMTP account, backup schedule, domain, or production
data restore have been provisioned or verified.
