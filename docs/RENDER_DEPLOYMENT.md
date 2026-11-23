# Render deployment for testing

## Current test deployment

On 23 November 2026, the Render Free test service reported deployment
`dep-dc1se1nf3r2c73dko37g` live for image/commit
`sha-8b0cdf94e173861aa314326176127529a0427253`. The public homepage and
`/admin-login.html` returned HTTP 200 after the Free instance woke. The live
login response still included `'unsafe-inline'`, `https://cdn.jsdelivr.net`,
and inline HTML handlers/styles, so the latest local security hardening is not
in this image. No successful account sign-in or end-to-end email delivery was
performed. The public service is
[`https://makerere-institute-of-technology.onrender.com/`](https://makerere-institute-of-technology.onrender.com/).
This is a test deployment, not production approval.

The active Free service uses `UPLOAD_DIR=/tmp/uploads`; uploads are ephemeral
and may be lost on restart or redeploy. The Free Blueprint now uses the same
path. Verify the service is connected to an isolated test database before
creating accounts or entering data. Never use real student records in this
environment.

The root `render.yaml` Blueprint is for a paid Starter service with a persistent
uploads disk. For public student testing on Render Free, select
[`render.free.yaml`](../render.free.yaml) as the Blueprint file instead. This
free service has no persistent disk, may sleep when idle, and must use a
separate, empty MySQL-compatible test database/schema with its own least-privilege
account. Never point it at the shared/live TiDB database or load real student
records. The free Blueprint keeps sample accounts and first-admin bootstrap
disabled; create only the test accounts you need after deployment.
Because the database starts empty, provision one named test administrator for
the initial deployment by temporarily setting `INITIAL_ADMIN_BOOTSTRAP_ENABLED`
to `true` and providing unique `INITIAL_ADMIN_USERNAME`,
`INITIAL_ADMIN_EMAIL`, and `INITIAL_ADMIN_PASSWORD` values in Render's private
environment settings. The password must be at least 12 characters. After the
first healthy startup, set the bootstrap flag to `false` and remove the three
bootstrap values. Use that administrator account to create the student test
accounts yourself; do not turn on `SAMPLE_DATA_ENABLED`.

For a newly created service from `render.free.yaml`, the Blueprint uses
Brevo's HTTPS transactional email API, avoiding Render Free's blocked outbound
SMTP ports. Configure `BREVO_API_KEY` and a sender address verified in Brevo
in Render's private environment. This describes the template; verify the active
image-based service's environment in the Render dashboard. Configure the exact
service HTTPS origin in both `FRONTEND_URL` and `CORS_ALLOWED_ORIGINS`. The
Blueprint requests automatic deployment after connected GitHub checks pass.
Connect the intended branch to GitHub Actions CI before enabling student access.

## Image-based Render service and release flow

The Render service consumes a prebuilt Docker Hub image rather than building
the Git branch directly. The GitHub Actions workflow publishes
`docker.io/ciphernelson/mit-portal:latest` and a commit-specific tag on pushes
to `main` and `fix/admin-login-offline-styles`, after the CI gates pass. The
current 23 November deployment uses
`docker.io/ciphernelson/mit-portal:sha-8b0cdf94e173861aa314326176127529a0427253`.
A Git push alone is not deployment evidence: confirm the workflow, the
deployed release, and service health for each release.

The public-admissions Turnstile integration is opt-in. For an approved test
domain, configure `TURNSTILE_ENABLED=true`, the public `TURNSTILE_SITE_KEY`,
and the private `TURNSTILE_SECRET_KEY` in Render's environment settings. Keep
the feature disabled until the keys and domain are verified with synthetic
submissions; never put the secret in source, screenshots, or browser code.
Cloudflare Turnstile offers a free plan, but provider keys, domain ownership,
and live verification have not been accepted for this test service.

The GitHub Actions repository secrets `DOCKERHUB_USERNAME` and
`DOCKERHUB_TOKEN` must be configured; never put registry tokens in workflow
files or chat. The active service is configured to use the commit-specific
image tag above. Verify the deployed image digest and application health after
each release; do not claim that a Git push updated the service until those
checks pass.

The CI workflow runs backend, MySQL migration, frontend, and browser tests and
builds the same Docker image used by Render. For local release verification,
run `.\scripts\render-release.ps1` from PowerShell with Node.js and Docker
Desktop available. The Docker build supplies Java 25 and runs Maven verification,
so a local Java installation is not needed. To trigger a deployment after
pushing a clean, verified commit, set `RENDER_DEPLOY_HOOK_URL` and
`RENDER_SERVICE_URL` in the PowerShell environment and add `-Deploy`; the script
waits for the service health endpoint. The script does not create external
databases or generate Render/Brevo credentials.

Render builds the commit on the branch configured for the service, not
uncommitted local files. Confirm the reviewed commit is pushed to that connected
branch and its GitHub Actions checks pass before deployment. Do not assume the
branch name in an older deployment record is still configured for Render;
verify the current setting in the Render service.
The CI workflow currently publishes Docker images only for pushes to `main`
and `fix/admin-login-offline-styles`; verify the Render-connected branch and
workflow condition before selecting a release path. A push to another branch
does not publish the image used by this service.

After a frontend authentication or service-worker change, verify the deployed
release commit and public login flows. The service worker uses a versioned cache;
increment `CACHE_NAME` when a release must invalidate previously cached assets.
For a copyable environment-variable starting point, use the root
`.env.render.example` with Render's **Add from .env** control. Replace every
`REPLACE_WITH_...` value with deployment-specific values before applying it;
do not use the local `.env` or an exported environment file. Keep credentials
and generated secrets in Render's secret environment fields. The example uses
the Blueprint's persistent upload disk at `/var/data/uploads`. Do not change
this to an ephemeral path if uploaded files must survive restarts or deploys.

The Free Blueprint uses Brevo over HTTPS for verification, password reset,
notifications, and admin test emails. Set `MAIL_PROVIDER=brevo`,
`MAIL_ENABLED=true`, `BREVO_API_KEY`, and `MAIL_FROM_EMAIL` to a sender address
verified in Brevo. Store the API key only in Render's secret environment. The
admin system status reports provider configuration and the time of its latest
accepted test email.
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

The Free Blueprint uses Brevo over HTTPS: set `MAIL_PROVIDER=brevo`,
`MAIL_ENABLED=true`, `BREVO_API_KEY`, and `MAIL_FROM_EMAIL` to a verified
Brevo sender. Store the API key only in Render's secret environment. Render
Free blocks outbound SMTP ports 25, 465, and 587, so configure Brevo's API,
not SMTP. Confirm delivery with an administrator test email; API acceptance
does not guarantee inbox delivery.

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
4. Configure the selected mail provider and send an administrator test email
   if email delivery is in scope.
5. Establish database and upload backup/restore procedures before importing
   real student, finance, or identity data.

This Blueprint is for a controlled test deployment, not a claim that the
external MySQL database, SMTP account, backup schedule, domain, or production
data restore have been provisioned or verified.
