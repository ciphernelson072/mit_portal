# Local Java hosting and PWA setup

This guide runs the existing Spring Boot application directly on Windows. It
keeps the student, teacher, administrator, database, email, payment, upload, and
backup integrations in the project; it does not replace them with a static-only
frontend.

## Requirements

- Windows 10/11 host with a reliable internet connection and power for any
  period students need to access the site.
- JDK 17 or newer. The project targets Java 17 and can run on a newer supported
  JDK.
- MySQL 8.0, running locally. The project and Docker stack are configured for
  MySQL 8.0. The bundled Laragon MySQL 8.4.x may not work with the current
  Flyway/Spring Boot combination; use a compatible MySQL 8.0 instance rather
  than changing migrations to work around it.
- The repository's `mvnw.cmd` wrapper (included); installing Maven separately
  is unnecessary.
- A domain and HTTPS tunnel/reverse proxy for a stable public URL. A temporary
  Cloudflare Quick Tunnel can be used for a short demonstration without a
  domain, but its URL changes and it has no uptime guarantee.

## 1. Create a local database

Start MySQL and create a database and application account using a private,
unique password. Run this in a MySQL client, replacing the example password:

```sql
CREATE DATABASE sis_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'sis_app'@'localhost' IDENTIFIED BY 'replace-with-a-unique-password';
GRANT ALL PRIVILEGES ON sis_db.* TO 'sis_app'@'localhost';
```

The account needs schema privileges because Flyway applies the project's
versioned migrations at startup. Do not expose MySQL's port 3306 to the
internet. Back up an existing database before the first startup after pulling
new migrations.

## 2. Start Java locally

From PowerShell in the project folder, set the database connection and fresh
local secrets in the current terminal session. Do not paste production
credentials into chat, source files, or a public repository.

```powershell
$env:DB_URL = 'jdbc:mysql://127.0.0.1:3306/sis_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC'
$env:DB_USER = 'sis_app'
$env:DB_PASSWORD = 'replace-with-the-database-password'

$bytes = New-Object byte[] 32
$generator = [System.Security.Cryptography.RandomNumberGenerator]::Create()
try { $generator.GetBytes($bytes) } finally { $generator.Dispose() }
$env:JWT_SECRET = [Convert]::ToBase64String($bytes)

.\run-sis.bat run
```

The local `dev` profile is the default. It listens only on `127.0.0.1:8080`,
keeps email disabled unless configured, and does not enable sample data. Open:

- Student login: `http://localhost:8080/index.html`
- Public admissions application: `http://localhost:8080/admissions.html`
- Public institutional site: `http://localhost:8080/`
- Staff login: `http://localhost:8080/admin-login.html`
- Health check: `http://localhost:8080/actuator/health/readiness`

Use the administrator workflow to create/approve accounts as intended by the
portal; a publicly reachable login page does not itself create student
accounts. Enable `SAMPLE_DATA_ENABLED=true` only on a disposable local test
database.

To build and test separately:

```powershell
.\mvnw.cmd verify
npm run test:unit
```

For browser acceptance on Windows, use the isolated runner:

```powershell
.\ops\run-e2e.ps1
```

It builds the app and starts a disposable H2/sample-data instance on port 18081
by default, separate from the normal portal on port 8080. It refuses to use an
occupied port and cleans up only its own server and temporary upload directory.
Choose another unused port with `-Port 18082`. The lower-level Playwright suite
accepts `BASE_URL` for an already-running test server; do not point it at a
production or real-data portal.

## 3. Keep existing integrations configured

The direct Java run uses the normal Spring configuration and the same MySQL
migrations as other deployments. Set optional credentials in the process
environment before `run-sis.bat run`; never add them to tracked property files.

- **Email:** off by default. For the local Cloudflare tunnel pilot, Resend over
  HTTPS is recommended: verify an institution-controlled sender domain, then
  set `MAIL_PROVIDER=resend`, `MAIL_ENABLED=true`, `MAIL_API_KEY`, and
  `MAIL_FROM_EMAIL` privately in the environment. For SMTP set `MAIL_ENABLED=true`
  and the `MAIL_*` settings documented in the README. Use an app password or
  provider API key, not an email account's primary password. The application
  receipt does not contain login credentials. After an administrator accepts
  and enrolls an applicant, the student receives a generated temporary
  password and username; the portal requires a password change before student
  services are available. Never email a permanent password.
- **Payments/webhooks:** payment configuration remains in the app. Configure
  `PAYMENT_WEBHOOK_SECRET` and the relevant provider settings only after the
  provider can reach the public HTTPS callback URL. Temporary tunnel URLs are
  unsuitable for dependable payment callbacks.
- **MFA:** optional; when enabled, configure a persistent `MFA_ENCRYPTION_KEY`.
- **Uploads and backups:** `UPLOAD_DIR` and backup marker paths must point to
  writable, backed-up local storage. Copy uploads and database backups to
  separate protected storage regularly; the PWA cache is not a backup.
- **External public links:** set `FRONTEND_URL` to the public site origin when
  email links, `robots.txt`, or `sitemap.xml` must advertise the public address.

Before any public deployment, use an isolated database and a dedicated
least-privilege database user, rotate credentials previously exposed outside
the server, configure backups, and check that all required integrations work.
Do not use real student, financial, or other sensitive data for an unreviewed
personal-host demo.

## 4. Short public HTTPS demo

A tunnel lets students open the Java application over the internet while the
Java server remains bound to loopback. Never port-forward the app or database
ports from the router.

1. Install `cloudflared` from Cloudflare's official distribution.
2. Start the app on `127.0.0.1:8080` as above and confirm local login works.
3. In another terminal, create the temporary tunnel and keep it running:

   ```powershell
   cloudflared tunnel --url http://localhost:8080
   ```

4. Open the HTTPS `trycloudflare.com` URL printed by the command. Anyone with
   the URL can reach the public pages.

Before testing authentication over HTTPS, stop the Java app with Ctrl+C. In
that same application terminal, set the cookie and both exact public origins,
then restart it; leave the tunnel process running:

```powershell
$env:COOKIE_SECURE = 'true'
$env:FRONTEND_URL = 'https://your-current-name.trycloudflare.com'
$env:CORS_ALLOWED_ORIGINS = 'https://your-current-name.trycloudflare.com'
.\run-sis.bat run
```

Test student login and public admissions flows. The URL changes when a Quick
Tunnel is recreated; update `FRONTEND_URL` and restart the app if needed. For a
stable student URL, use a domain you control with a persistent,
institution-approved tunnel or host. This local setup has
no uptime guarantee: students lose access whenever the computer, tunnel, power,
or internet connection goes offline.

The public homepage and student portal are installable as one PWA over the
HTTPS tunnel. On a supported desktop/Android browser, open the current URL and
choose **Install portal** or the browser's **Install app** menu item. On iPhone
or iPad, use Safari's Share menu and choose **Add to Home Screen**. A Quick
Tunnel has a different origin after restart, so an installation made from its
old URL will not follow the new URL; use a named tunnel and your own domain for
a lasting installation.

`OriginValidationFilter` checks browser writes against the configured origin
allow-list, so use the tunnel's exact HTTPS origin in
`CORS_ALLOWED_ORIGINS`—no path, wildcard, or trailing slash. The default
development list contains only localhost origins and would reject public
login/admissions requests.

The local tunnel connector can make requests appear to the app as one proxy
address. Check the application's rate-limit behavior before inviting a large
group; do not enable forwarded-client-IP trust unless only the intended trusted
proxy can connect directly to the app.

## 5. Install and use the PWA

The Spring Boot server serves the same frontend and `/api` backend from one
origin. Students can visit the HTTPS public URL in a supported mobile/desktop
browser and use its **Install app** or **Add to Home Screen** action. PWA
installation requires HTTPS (or localhost for development); an HTTP LAN address
is not a reliable install target.

The service worker caches static app pages/assets only. Authentication, student
records, and every `/api/**` response stay network-only and are not available
offline. The installed PWA still needs the public Java server and internet
connection for sign-in and live portal features. It is another way to open the
online portal, not a separate offline database or an independent hosting
service.

For any real institutional launch, use an institution-controlled always-on
server/domain, HTTPS, reviewed production configuration, monitoring, and
tested database/upload backups. A personal computer plus a Quick Tunnel is for
temporary testing only.

## Official references

- [Cloudflare Quick Tunnels](https://developers.cloudflare.com/tunnel/get-started/quick-tunnels/)
- [MDN: Progressive Web Apps](https://developer.mozilla.org/docs/Web/Progressive_web_apps)
