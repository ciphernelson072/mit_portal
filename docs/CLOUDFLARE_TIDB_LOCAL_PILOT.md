# Cloudflare Tunnel + local Java + TiDB Cloud pilot

This setup publishes the existing Spring Boot portal from a Windows computer
through Cloudflare Tunnel. The Java application connects outbound to TiDB Cloud;
students' browsers connect only to the public HTTPS portal. Never publish
MySQL/TiDB port 4000 or expose the database password to browser code.

## Be clear about the operating model

- This is a globally reachable **pilot**, not managed Java hosting. The
  computer, Java process, power, and internet connection must remain available.
- Use a **named Cloudflare Tunnel and a domain you control** for a stable
  hostname. A Cloudflare Quick Tunnel needs neither account nor domain but
  generates a temporary hostname and is documented for development/testing.
- TiDB Cloud Starter's current free quota is 5 GiB row storage, 5 GiB columnar
  storage, and 50 million request units per month per first five Starter
  instances. At quota, new connections are denied until reset or a spending
  limit is added. Do not treat that tier as an availability guarantee.
- TiDB is MySQL-compatible, but compatibility is not identity. Run the
  project's migrations and acceptance tests against a **new, disposable TiDB
  database first**. Do not point initial testing at production or a populated
  database. The project has not yet certified all migrations against TiDB.
- Do not put real student, identity, or financial records on this personal
  pilot unless the institution approves the data processing, hosting, backups,
  and support arrangement. Online payment-provider processing is not ready for
  real transactions; keep unverified channels disabled.

For an actual institutional production service, move the Java runtime to an
approved always-on host and complete the open items in the release checklist.
Cloudflare Tunnel can remain in front of that host.

## 1. Create and secure the TiDB Cloud database

1. Create a TiDB Cloud Starter instance in a region suitable for the expected
   students. Save the endpoint, cluster username prefix, database password,
   and database name privately.
2. Create an application database (for example `mit_student_portal`) and a
   dedicated non-root application user. TiDB Starter usernames include the
   cluster prefix; use the exact generated format, such as
   `CLUSTER_PREFIX.sis_app`.
3. Restrict the TiDB network access list to the Java host's public outbound IP
   where practical. The Java host connects to TiDB directly over TLS; Cloudflare
   Tunnel does **not** hide the database connection or proxy it.
4. Confirm whether the host has a stable outbound IP. If it changes, IP
   allowlisting can stop the app connecting; do not solve that by opening
   database access broadly without an approved risk decision.
5. Ensure the target application database is empty. The production profile
   runs Flyway migrations with automatic baseline disabled. Back up any
   existing data and prepare an explicit migration/adoption plan before using
   a non-empty database.

TiDB Cloud Starter connections require TLS. The local config validates that
the JDBC URL uses port `4000` and `sslMode=VERIFY_IDENTITY`.

## 2. Create a named Cloudflare Tunnel

1. Add a domain you control to your Cloudflare account and ensure its DNS is
   active there. A named public hostname requires a domain; Cloudflare Tunnel
   itself does not provide a permanent free `trycloudflare.com` hostname.
2. In Cloudflare Dashboard, create a remotely managed tunnel and select
   Windows for its connector. Install the `cloudflared` Windows service using
   the command/token Cloudflare displays. Treat that token as a secret: never
   put it in the repository, `.env` file, screenshot, or chat.
3. Add a **Published application** route:
   - Hostname: for example `portal.your-domain.example`
   - Service: `http://127.0.0.1:8080`
4. Start/check the Cloudflare connector service. Keep inbound router
   port-forwarding disabled; Cloudflare Tunnel uses an outbound connection.
   Do not expose local ports 8080 or 3306 to the public internet.

The Cloudflare route can exist before Java starts. The site becomes available
after both the tunnel and the Java app are healthy.

## 3. Configure the project without committing secrets

From PowerShell in the project root:

```powershell
Copy-Item .env.cloudflare-tidb.example .env.cloudflare-tidb
notepad .env.cloudflare-tidb
```

Replace the placeholders in the ignored `.env.cloudflare-tidb` file:

```dotenv
SPRING_PROFILES_ACTIVE=prod
DB_URL=jdbc:mysql://YOUR_TIDB_HOST:4000/mit_student_portal?sslMode=VERIFY_IDENTITY&serverTimezone=UTC
DB_USER=YOUR_CLUSTER_PREFIX.sis_app
DB_PASSWORD=your-private-TiDB-password
JWT_SECRET=your-own-random-secret-at-least-32-characters
PAYMENT_WEBHOOK_SECRET=a-different-random-secret-at-least-32-characters
FRONTEND_URL=https://portal.your-domain.example
CORS_ALLOWED_ORIGINS=https://portal.your-domain.example
COOKIE_SECURE=true
COOKIE_SAME_SITE=Strict
SERVER_PORT=8080
UPLOAD_DIR=./uploads
SAMPLE_DATA_ENABLED=false
FLYWAY_BASELINE_ON_MIGRATE=false
MAIL_ENABLED=false
AUTH_TRUSTED_PROXY_IPS=127.0.0.1,::1,::ffff:127.0.0.1
AUTH_TRUST_X_FORWARDED_FOR=true
```

Use actual host/database/user details from TiDB and the exact HTTPS hostname
configured on the Cloudflare route. Generate fresh signing secrets locally;
never reuse a password or signing secret previously exposed. The example file
is tracked but contains placeholders only; `.env.cloudflare-tidb` is ignored.
The launcher does not print secret values.

### Configure outbound email

When outbound email is configured, applicants receive an application receipt
without login credentials. After an administrator accepts and enrolls an
applicant, the portal emails the generated username and temporary password.
Student accounts must change that password before accessing student services.
If delivery is unavailable, the administrator receives a one-time display for
private handoff. Never email or share a permanent password.

For the requested pilot sender, `nelliedelson@gmail.com`, use Gmail SMTP
with a Google App Password. This can be used without owning a domain, but is a
personal mailbox rather than an institutional sender and remains subject to
Google sending limits, network access to outbound port 587, and spam
filtering. Enable 2-Step Verification on the Google account, then open
[Google App Passwords](https://myaccount.google.com/apppasswords) and create
an app password for the portal. Google may not offer App Passwords for some
managed or advanced-protection accounts. Do not use the account's normal
password and never paste the app password into chat.

With the portal project open in PowerShell, run:

```powershell
.\ops\configure-email.ps1
```

The helper defaults to Gmail SMTP and the requested sender address. It asks
for an inbox to test, accepts the App Password through a hidden secure prompt,
sends a real test email, and only updates the ignored `.env.cloudflare-tidb`
file after Gmail accepts the message. It then restarts the existing scheduled
task and waits for app readiness. Confirm the test email arrived (including
the spam folder) before relying on student delivery.

If you prefer to save the credential in the private environment file first,
put the App Password on the `MAIL_PASSWORD=` line (remove the spaces if
Google groups it), keep `MAIL_ENABLED=false`, and save. Then run:

```powershell
.\ops\configure-email.ps1 -UseEnvironmentCredential -TestRecipient nelliedelson@gmail.com
```

The helper reads that private setting without displaying it, tests Gmail, and
only then enables email and restarts the portal.

For long-term institutional sending, Resend over HTTPS remains preferred.
That option requires an institution-owned domain added to Resend, the exact
Resend SPF/DKIM records published with its DNS provider, and the domain marked
verified. Review existing SPF and DMARC policy before changing DNS. Then
create a new send-only key restricted to the verified sender domain; do not
reuse the existing `MIT Portal Render` key. Run the same helper with
`-Provider resend` to test and configure it. Never use the temporary
`trycloudflare.com` hostname as an email sender domain.

Check the configuration before connecting:

```powershell
.\ops\run-cloudflare-tidb.ps1 -ValidateOnly
```

The check verifies the public HTTPS origin, strict secure cookies, production
profile, loopback binding, TiDB port/database/TLS mode, non-root prefixed user,
placeholder secrets, and disabled demo data. This validates settings only; it
does not prove credentials, network allowlisting, SQL compatibility, or
migration success.

## 4. Start and verify

Start Java in one PowerShell window:

```powershell
.\ops\run-cloudflare-tidb.ps1
```

The launcher binds Spring Boot only to `127.0.0.1:8080` and uses the production
profile for secure cookies and startup guardrails. In another window, confirm
the local app is ready:

```powershell
Invoke-RestMethod http://127.0.0.1:8080/actuator/health/readiness
```

Then open the configured HTTPS hostname from a phone or another internet
connection. Verify the public home and admissions pages, student login,
administrator login, database-backed reads/writes, secure-cookie behavior,
email only if configured, uploads, and logs. Never share the actuator endpoint
or expose the database port.

### First administrator on a brand-new database

The project does not create a default administrator. On the first startup of
an empty application database only, temporarily set
`INITIAL_ADMIN_BOOTSTRAP_ENABLED=true` and provide a unique
`INITIAL_ADMIN_USERNAME`, `INITIAL_ADMIN_EMAIL`, and a private
`INITIAL_ADMIN_PASSWORD` of 12-72 UTF-8 bytes in `.env.cloudflare-tidb`.
Start the app once and verify the admin can sign in. Then stop Java, set the
bootstrap flag back to `false`, clear all three bootstrap values, and start it
again. The bootstrap claim is one-time; do not enable it as a recovery
mechanism or against a populated database. The launcher checks the credential
format but cannot establish that the database is empty.

Because the app is bound to loopback and only local `cloudflared` is trusted to
forward client IPs, the launcher configures the rate limiter to use the
forwarded client address only from the loopback proxy. It does not trust
forwarded-IP headers from arbitrary network clients.

### Start automatically after Windows sign-in

The optional current-user Scheduled Task starts the Java app and an account-less
Cloudflare Quick Tunnel after you sign in to Windows. It does not need
Administrator rights, does not install a Windows service, and keeps the app
bound to `127.0.0.1`. In PowerShell from the project root, run:

```powershell
.\ops\install-cloudflare-tidb-autostart.ps1
```

To start it now without waiting for the next sign-in:

```powershell
Start-ScheduledTask -TaskName 'MIT Student Portal Cloudflare TiDB'
```

Each Quick Tunnel gets a new temporary URL. The startup task updates only the
private `FRONTEND_URL` and `CORS_ALLOWED_ORIGINS` entries to match, then opens
the portal when readiness checks pass. The latest URL is saved in
`%LOCALAPPDATA%\MITPortal\portal-url.txt`; startup and application logs are
stored in `%LOCALAPPDATA%\MITPortal\logs`. To remove the automatic task, run:

```powershell
.\ops\install-cloudflare-tidb-autostart.ps1 -Uninstall
```

### Start the public pilot manually whenever you need it

Open PowerShell as the same Windows user that owns `.env.cloudflare-tidb`,
then run this from the project directory:

```powershell
Set-Location 'C:\laragon\www\student_portal\mit_portal-main'
.\ops\start-cloudflare-tidb-at-logon.ps1
```

This starts a fresh Quick Tunnel and the TiDB-backed app, prints the new public
URL, saves it to
`$env:LOCALAPPDATA\MITPortal\portal-url.txt`, and opens it in your browser.
The PowerShell command stays active while the public pilot is running. Use a
second PowerShell window to read the latest URL:

```powershell
Get-Content "$env:LOCALAPPDATA\MITPortal\portal-url.txt"
```

If the pilot is already running, do not start another copy; read the saved URL
instead. To use the Scheduled Task already installed for this Windows account
when the app is stopped, run:

```powershell
Start-ScheduledTask -TaskName 'MIT Student Portal Cloudflare TiDB'
```

Every new Quick Tunnel has a different hostname. Share the current URL, not a
URL from a previous run. Keep the startup PowerShell session open while using
the manual mode. The service is globally reachable only while this PC, its
internet connection, Java process, and tunnel remain running; it is a temporary
pilot, not permanent hosting.

This runs after the Windows user signs in, not before anyone logs in. The PC
must have power and internet, and must not sleep, for students to reach the
portal. Quick Tunnel URLs are temporary and are not an uptime guarantee or a
production hostname. Use a named tunnel and a domain you control for a stable
public URL. Keep the host account protected and do not use this personal pilot
for real student records without institutional approval.

## 5. Database compatibility, backups, and limits

- First verify all Flyway migrations on a fresh, disposable TiDB database.
  Then test representative reads/writes, transactions, unique constraints,
  date/time handling, reports, uploads, and role isolation. Stop if a migration
  or query fails; do not disable validation or baseline to bypass the error.
- TiDB Starter's free quota is limited. Monitor its usage; quota exhaustion can
  deny new connections. Decide in advance what should happen if quota is
  reached. Do not configure surprise pay-as-you-go spending.
- TiDB-managed backups/snapshots and an off-site restore plan must be verified
  for the selected tier. The project's local `backup-mysql.ps1` helper is for
  ordinary local MySQL and is **not** proof of encrypted TiDB Cloud backups.
  Keep uploads backed up separately from database snapshots.
- The Windows pilot can create checksum-verified local TiDB dumps and upload
  archives with `ops\run-tidb-backups.ps1`. Install the pinned TiDB Dumpling
  v8.5.8 package with `ops\install-tidb-dumpling.ps1`; the exporter runs in
  Docker Desktop's Linux engine using snapshot consistency and CA-verified
  TLS. Install its daily current-user schedule with
  `ops\install-tidb-backup-task.ps1`. Artifacts are stored in the
  account-protected `%LOCALAPPDATA%\MITPortal\backups` directory, and the
  database and upload backups run independently. The admin status card verifies
  artifact existence, checksum, and freshness only. These local backups are
  **not encrypted or off-host** and are not sufficient for real institutional
  records. Verify TiDB-managed snapshots, encrypted off-site retention, and a
  coordinated restore before production use.
- TiDB Cloud does not expose PD directly to Dumpling, so the tool cannot
  automatically extend TiDB's GC safe point. The current pilot GC lifetime is
  `10m0s`; Dumpling runs tested against TiDB Cloud Serverless did not complete
  an export. Do not install the combined database/upload schedule until an
  operator approves and tests a bounded temporary `tidb_gc_life_time`
  extension with guaranteed restoration of the prior value on success,
  failure, and timeout. Do not leave extended retention in place, and do not
  write a database success marker unless the full dump and checksum verify.
  See [TiDB Dumpling's GC guidance](https://docs.pingcap.com/tidb/stable/dumpling-overview/#manually-set-the-tidb-gc-time).
- The configured pilot database account currently has privileges on the
  application schema only, not cluster-wide privileges to adjust the GC
  lifetime. TiDB Cloud's backup guide covers Dedicated clusters; a
  provider-managed downloadable backup workflow has not been verified for this
  Starter/Serverless pilot. Therefore, keep the upload-only task installed and
  leave Database Backup unhealthy until a supported managed export or an
  authorized operator procedure is available.
- If the TiDB export is unavailable, install only the upload schedule with
  `ops\install-tidb-backup-task.ps1 -UploadOnly`. This keeps the upload status
  fresh without repeatedly attempting the database dump; it does not mark the
  database backup healthy.
- The current app rate limits and uploaded files are local/single-instance.
  This design is one Java host; do not add Java replicas without shared rate
  limiting and shared file storage.
- The host going offline, sleeping, restarting, losing internet, or changing
  network can take the whole portal offline. Configure Windows power settings
  only on a dedicated, institution-approved host; this does not create a
  service-level guarantee.

## Official product references

- [Cloudflare Tunnel setup and published routes](https://developers.cloudflare.com/tunnel/get-started/)
- [Cloudflare Quick Tunnel limitations](https://developers.cloudflare.com/tunnel/get-started/quick-tunnels/)
- [TiDB Cloud plans and Starter quotas](https://docs.pingcap.com/tidbcloud/select-cluster-tier/)
- [TiDB Cloud TLS connections](https://docs.pingcap.com/tidbcloud/secure-connections-to-serverless-clusters/)
- [TiDB Cloud Starter spending limits](https://docs.pingcap.com/tidbcloud/manage-serverless-spend-limit/)
