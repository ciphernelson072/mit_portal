# Makerere Institute of Technology — Student Information Portal

**Opening this in IntelliJ IDEA?** See [`docs/IDE_SETUP.md`](docs/IDE_SETUP.md) first - it covers
the Lombok plugin/annotation-processing setup, which is the most common reason this
kind of project shows red errors in the IDE despite building fine from the terminal. 

## Engineering Highlights

- Refresh-token authentication flow and server-revocable sessions.
- DTO-first request and response boundary across controllers and services.
- Rate limiting and CORS/CSP hardening already attached to the request filter chain.
- Test-backed grade approval, email, and file upload flows.
- Centralized exception and API response contract handling.

See [`docs/DEVELOPMENT_LOG.md`](docs/DEVELOPMENT_LOG.md) for the detailed security and architecture history.
For the current outstanding work and phased Render deployment runbook, see
[`docs/REMAINING_WORK_AND_DEPLOYMENT.md`](docs/REMAINING_WORK_AND_DEPLOYMENT.md).
For administrator procedures and synthetic practice data, see
[`docs/ADMIN_OPERATIONS_PRACTICE_GUIDE.docx`](docs/ADMIN_OPERATIONS_PRACTICE_GUIDE.docx).
For the reviewed system gaps, corrections, and readiness recommendations, see
[`docs/SYSTEM_ANALYSIS_AND_RECOMMENDATIONS.docx`](docs/SYSTEM_ANALYSIS_AND_RECOMMENDATIONS.docx).
For the implemented assessment, retake, approval, and publication workflow and
its unresolved academic-policy decisions, see
[`docs/ACADEMIC_RESULTS_WORKFLOW.md`](docs/ACADEMIC_RESULTS_WORKFLOW.md).

## Screenshots

Capture login, student, teacher, administrator, and mobile views after browser
acceptance testing. Store dated release captures under `docs/screenshots/`.

## Quick Start

### Local Java run (no Docker required for the app)

Install JDK 17 or newer and a supported local MySQL 8.0 server, then follow
[`docs/LOCAL_JAVA_AND_PWA_HOSTING.md`](docs/LOCAL_JAVA_AND_PWA_HOSTING.md) to
create the database, set private environment variables, and run Spring Boot
directly on Windows. The Maven wrapper is included; a separate Maven install is
not required.

For a short, public HTTPS demo, that guide also covers an HTTPS tunnel and PWA
installation. Keep a dedicated host online for students to connect, and do not
expose real student data through a temporary tunnel.

For the named Cloudflare Tunnel + TiDB Cloud local-Java pilot, follow
[`docs/CLOUDFLARE_TIDB_LOCAL_PILOT.md`](docs/CLOUDFLARE_TIDB_LOCAL_PILOT.md).
Test migrations against a fresh disposable TiDB database first; this setup is
not an always-on production host and TiDB migration compatibility is not yet
certified.

### One-click Windows Docker automation

```powershell
./run-sis.bat docker-up
```

This creates `.env` from `.env.example` when missing, runs a local Docker
preflight check, uses Docker Compose with MySQL 8.0 for a Flyway-compatible
schema, and waits for the portal health endpoint before reporting the ready URLs.
If Docker is not running, the script stops with a clear message explaining that the
bundled Laragon MySQL 8.4.x is not compatible with this project’s Flyway/Spring
Boot stack. Stop the stack with:

```powershell
./run-sis.bat docker-down
```

For a full reset of the local database volume:

```powershell
./run-sis.bat docker-reset
```

### Manual startup

```powershell
.\run-sis.bat run
```

The direct Windows launcher reads `MAIL_*` settings and configured backup
marker paths from the local `.env` file; database and other runtime settings are
not imported from that Docker-oriented file. Values already set in the process
environment take precedence. When unset, upload-backup status uses
`backups\.uploads-backup-success`, matching the local upload backup script.
SMTP secrets are never printed by the launcher.

To rebuild the demo stack from scratch with a clean database volume:

```sh
docker compose down -v && docker compose up --build
```

Docker requires the variables in `.env.example` to be set; copy that file to `.env`
and replace every placeholder before startup. Compose binds the application to
`127.0.0.1` by default and restarts the app and database after host restarts; put
a TLS-terminating reverse proxy on the same host for remote production access.
phpMyAdmin is disabled unless the `admin-tools` profile is enabled and remains
bound to loopback. For a deliberate LAN demo on a trusted Private network,
set `APP_BIND_ADDRESS` to the host's exact Wi-Fi IPv4 address (never `0.0.0.0`)
and set matching `CORS_ALLOWED_ORIGINS` and `FRONTEND_URL` values. Do not enable
LAN access while Windows classifies the network as Public. The
application container runs the Flyway migrations against a new database volume.
Sample accounts are disabled by default; set `SAMPLE_DATA_ENABLED=true` only for a
local demo deployment.

#### SMTP setup

To enable outbound email, set `MAIL_ENABLED=true` and provide a valid SMTP account
in `.env` before starting the app. For Gmail, create an app password in Google
Account → Security → App passwords, then set:

```env
MAIL_ENABLED=true
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=your-address@gmail.com
MAIL_PASSWORD=your-16-character-app-password
MAIL_STARTTLS_ENABLE=true
MAIL_STARTTLS_REQUIRED=true
MAIL_SMTP_SSL_ENABLE=false
```

Do not reuse the primary Gmail password; Gmail expects an App Password for SMTP.
The app will reject mail delivery when `MAIL_ENABLED=true` but the host/username/
password values are blank or still left at placeholders.

For Render Free, use the Resend HTTPS API instead of SMTP because Render Free
blocks outbound SMTP ports 25, 465, and 587. Set `MAIL_PROVIDER=resend`,
`MAIL_API_KEY` to a Resend API key, and `MAIL_FROM_EMAIL` to a verified Resend
sender. The API sends over HTTPS and does not require SMTP host, port, or
username settings. Never commit API keys.

The canonical
frontend is the split static frontend under `src/main/resources/static/`: use
`index.html` for student authentication, `admin-login.html` and
`admin-dashboard.html` for administrators, and `teacher-dashboard.html` for
teachers. The previously documented `js/app.js` and `css/theme.css` architecture
does not exist and must not be treated as an active implementation.
The public institutional website is served at `/` and
[`src/main/resources/static/public/index.html`](src/main/resources/static/public/index.html)
with About, Programmes, Admissions, and Contact pages. Its programme catalogue
and current academic year use the existing public admissions API, and application
submission/status checks continue to use `/admissions.html`. The student portal
login remains available at `/index.html`; authenticated dashboard routes and
workflows are unchanged.

Public programme cards include the configured programme duration. Applicants
receive a copyable application number, a plain-language status explanation, and
an applicant-specific privacy notice. The public pages also expose
`/robots.txt` and `/sitemap.xml`, built from `FRONTEND_URL`; production must set
that variable to the actual public HTTP(S) site origin. No canonical URL or
absolute social-preview image is guessed. Before launch, obtain approval for the
campus photo and logo, publish verified admissions requirements/fees/deadlines
and location details, verify institutional ownership of the supplied email and
WhatsApp contact, and have the institute approve its
applicant-data retention schedule and privacy contact. The privacy page
explicitly notes the missing retention/contact details rather than inventing
policy. The footer links directly to Facebook, Instagram, LinkedIn, YouTube,
WhatsApp, X, and TikTok. The supplied WhatsApp contact is linked in international
format to `+256 769 234 213`; the supplied email is `ciphernelson072@gmail.com`.
The general platform links are temporary destinations and should be replaced
with MIT's official profiles once those URLs are confirmed. Verify institutional
ownership of the supplied email and WhatsApp account before production use.

The Docker image is built in multiple stages and contains only the packaged
application JAR and Java runtime. Local `.env` files, uploads, test outputs,
development dependencies, and documentation are excluded from the Docker build
context; keep deployment secrets in the deployment environment, not in the image.

### First-run admissions setup

An administrator can configure the institution directory from **Admin Dashboard →
Academic Operations**. Add the institution's actual campus, faculty, department,
and programme records; programmes created there become available to public
admissions. Do not use invented or sample institutional names in a live system.
Create the applicable academic year and choose whether it should be active.
Activating a year deactivates the previously active year, so only one is current
for admissions. Duplicate directory/year codes are rejected.
Application decisions are available under **Admin Dashboard → Admissions**.
The dashboard's Pending Actions card links to this queue and to pending grade
approvals; both counts are loaded from their respective review queues. An
accepted applicant can be enrolled into an active student account with a
matching email address. If an account does not exist, the admin flow creates
one using the applicant's details and an administrator-supplied initial
password and enrolls it in one database transaction; an enrollment failure
rolls back the new account. The application email and programme are taken from
the accepted application rather than trusted from the form. Share that initial password securely and instruct
the student to replace it promptly. Admin-provisioned student accounts are
restricted to changing their password until they do so; the restriction is
enforced by the API, not only by the page flow. Verify account recovery email
delivery is configured before relying on the recovery workflow. The admissions
queue displays an enrolled state after enrollment and no longer offers a
duplicate enrollment action for that application. Applicants checking a
submitted application must provide both its application number and the email
address used to apply; the status-check request sends both in a POST body and
returns only the programme, academic year, and current status.
Public application submissions and status checks are rate-limited per client
IP (5 requests per minute by default); configure
`RATELIMIT_ADMISSIONS_CAPACITY` and
`RATELIMIT_ADMISSIONS_REFILL_PER_MINUTE` to adjust this for a deployment.
Each email address can have at most one application per academic year. Email is
trimmed and lowercased before storage, and a database unique constraint prevents
simultaneous duplicate submissions. Before applying Flyway migration V28 to an
existing database, inspect and resolve any existing duplicates without deleting
application records:

```sql
SELECT LOWER(TRIM(email)) AS normalized_email, academic_year_id, COUNT(*) AS application_count
FROM admission_applications
GROUP BY LOWER(TRIM(email)), academic_year_id
HAVING COUNT(*) > 1;
```

The dashboard system status checks database connectivity and email
configuration. After a successful administrator test email, it reports that
SMTP accepted the test for the lifetime of that application process; it does
not independently verify inbox delivery. Configure the external backup script
to write its `.backup-success` marker and set `BACKUP_STATUS_FILE` to that
marker path when using a deployment other than the direct Windows launcher.
Each marker identifies its artifact and checksum; the dashboard reports recent
only while both are present and the sidecar matches the marker. For upload backups, set
`UPLOAD_BACKUP_STATUS_FILE` to the `.uploads-backup-success` marker in the
backup output directory. `BACKUP_MAX_AGE_HOURS` (36 by default) determines
when either displayed backup status becomes stale. After updating to this
marker format, run each backup script once to replace any older timestamp-only
markers.
Run `ops/backup-mysql.ps1` from a secured scheduled task with `DB_PASSWORD`
provided to that task's process environment. The scripts pass MySQL credentials
through a short-lived option file restricted to the current Windows account,
not a command-line password argument. Each dump is published with an
adjacent `.sha256` checksum, which `ops/restore-mysql.ps1` verifies before
sending SQL to MySQL. Older backups without a checksum require the explicit
`-AllowUnverifiedBackup` option; use it only for a trusted backup file.
Database and upload backup scripts retain timestamped artifacts for 30 days by
default and remove older matching artifacts and checksum sidecars only after a
new backup succeeds. Set `-RetentionDays` to adjust this window; partial and
unrelated files are not part of the cleanup.
The upload directory configured by `UPLOAD_DIR` must be backed up separately:
run `ops/backup-uploads.ps1 -UploadDirectory $env:UPLOAD_DIR` and restore with
`ops/restore-uploads.ps1` to a new destination path. Upload archives include a
SHA-256 sidecar and restores reject missing or mismatched checksums and unsafe
archive paths. The upload backup script writes its success timestamp marker
only after publishing the archive and checksum. Coordinate database and upload
backups to keep metadata and files consistent.
The status confirms only that the script reported a successful dump; it does
not verify restoreability or off-site retention, which must be tested and
monitored separately.

For the Windows Cloudflare/TiDB pilot, run `.\ops\run-tidb-backups.ps1` once
to create the database and upload backups and populate the admin dashboard's
two backup statuses. Install the pinned TiDB Dumpling v8.5.8 package first with
`.\ops\install-tidb-dumpling.ps1`; it verifies the official package SHA-256.
The TiDB export runs in Docker Desktop's Linux engine with snapshot consistency
and CA-verified TLS. The private `.env.cloudflare-tidb` settings are required.
TiDB Cloud does not expose PD directly to Dumpling, so Dumpling cannot extend
the GC safe point automatically. The TiDB export must not be scheduled until an
operator has approved a bounded, temporary `tidb_gc_life_time` extension and
verified that the original value will be restored even when the export fails.
Do not extend GC retention indefinitely or write a success marker for an
incomplete export. See the [TiDB Dumpling GC guidance](https://docs.pingcap.com/tidb/stable/dumpling-overview/#manually-set-the-tidb-gc-time).
The pilot database account currently has privileges only on the application
schema, so it cannot perform this cluster-wide change; the observed GC lifetime
is `10m0s`. The TiDB Cloud backup guide covers Dedicated clusters, and no
provider-managed downloadable backup workflow has been verified for this
Starter/Serverless pilot. Until a supported managed export or an authorized
operator procedure is available, keep the upload-only schedule installed and
leave Database Backup unhealthy.
Install a daily current-user task with `.\ops\install-tidb-backup-task.ps1`
(default 02:15 local time); remove it with `-Uninstall`. Database and upload
backups run independently, so a failed database export does not suppress the
upload archive. If TiDB export is unavailable, use
`.\ops\install-tidb-backup-task.ps1 -UploadOnly` to keep upload status fresh
without repeatedly attempting the database export; the database status remains
unhealthy until a verified full dump succeeds. The local backup folder is
access-restricted to the current account, SYSTEM, and administrators, but the
artifacts are not encrypted or stored off-host. Do not treat these pilot
backups as sufficient protection for real institutional records.

For direct Laragon development, start the application with `run-sis.bat run`
or `ops/run-local.ps1`. The launcher sets the local database backup marker and
creates a checksum-verified dump when the last valid dump is more than 24 hours
old. It uses the active local MySQL server's matching `mysqldump` client and
the same host/database/user settings as the development profile. If the local
database account has no password, the launcher permits that only for this
explicit local backup flow; provide `DB_PASSWORD` in the process environment
when the account requires one. A failed or unavailable dump is reported as a
warning and is never marked successful.

For a controlled Render test deployment, follow [docs/RENDER_DEPLOYMENT.md](docs/RENDER_DEPLOYMENT.md)
and create a Blueprint from the root-level `render.yaml`. The application
currently requires an external MySQL-compatible database; Render's managed
PostgreSQL is not compatible without a database/driver/migration conversion.

For Serv00, use [docs/SERV00_DEPLOYMENT.md](docs/SERV00_DEPLOYMENT.md) and run
`.\ops\prepare-serv00.ps1` from the project root to build a verified archive
without the optional demo seeder or sample SQL.

The Docker Compose stack mounts the host backup directory read-only inside the
app container. `run-sis.bat docker-up` starts the database first and attempts a
real checksum-verified dump before starting the app, placing the marker and
artifact at paths visible inside that mount. It uses the `MYSQL_PORT` mapping
plus `DB_USER`/`DB_PASSWORD` from `.env`, plus a host `mysqldump` client. If Docker
Desktop is unavailable or that client/credential is missing, startup reports a
warning and the dashboard correctly remains unhealthy; never create or copy a
success marker without a matching dump and checksum. For other Compose startup
methods, run a compatible backup job that writes to the same host backup mount.

Administrators can send individual email campaigns from **Messages → Email all
users**. The recipient picker includes active accounts with a valid account
email, falling back to a valid student or teacher profile email when needed. It
supports search and role filtering, and sends a separate message to each
selected recipient so recipient addresses are not disclosed. Campaigns are
limited to 450 recipients per send; missing or deactivated accounts are rejected
before any delivery, and partial SMTP failures are reported by username.

The admin **File Management** page lists files under the configured upload
directory (up to 500 entries per listing), supports path search, and offers
inspect, download, and delete actions. The API listing is administrator-only;
downloads still use the existing file-access policy. Deletion is refused when a
student, teacher, course, research, or accreditation record still references
the file.

The admin sidebar's **Messages**, **File Management**, and **Operations
Workspace** links open their dedicated pages. The Operations Workspace runs
the configured API workflows: read-only requests without parameters load
directly, while requests with route or query parameters open a form that builds
the request from validated fields instead of requiring manual URL edits.

Docker Compose publishes the application and MySQL ports on loopback by
default (`127.0.0.1:8080` and `127.0.0.1:3306`). For an isolated local stack,
`APP_PORT` and `MYSQL_PORT` can select unused host ports without changing the
container ports. `UPLOADS_HOST_DIR` and `BACKUPS_HOST_DIR` select the host
directories mounted for uploads and read-only backup-status verification;
the defaults are `./uploads` and `./backups`. Compose configures the admin
system-status endpoint to read the success markers from `/app/backups`.
When backing up a Compose database published on a non-default host port, pass
that port with `-DatabasePort` to `ops/backup-mysql.ps1` and
`ops/restore-mysql.ps1`. Treat `ops/start-local-dev.ps1 -Reset` as destructive:
it removes the Compose-managed database volume and now prompts for confirmation.

### Optional multi-factor authentication

MFA is disabled by default. To enable it, set `MFA_ENABLED=true` and provide
`MFA_ENCRYPTION_KEY` as a Base64-encoded 32-byte AES key (for example, generate one
with `openssl rand -base64 32`). Keep this key private and persistent across
restarts and backups; losing or changing it makes enrolled authenticator secrets
undecryptable. Users enroll from Profile → Security. Enabling or disabling MFA
invalidates active sessions. Students, teachers, and administrators can opt in
from the Account Security page linked in their dashboard menus; enrollment is not
required. After enrollment, sign-ins require the six-digit code from the user's
authenticator app. Configure the key in the environment file used by the active
launcher, and do not commit that file or reuse a key across separate environments.

## Local Demo Credentials

Use these accounts only with local sample data. Do not seed them in production and
change every password before any shared demonstration:

- `admin` / `admin123`
- `teacher1` / `teacher123`
- `teacher2` / `teacher123`
- `student1` / `student123`
- `student2` / `student123`

## Overview

This Makerere Institute of Technology Student Information Portal is a Spring Boot
institutional portal for the Makerere Institute of Technology community, with
advanced academic, administrative, finance, attendance, and PWA-facing workflows.

- ✅ **Full CRUD Operations** with soft delete for data preservation
- ✅ **CGPA System** with weighted calculations based on course credits
- ✅ **Role-specific static frontend** covering administrator, teacher, and student
   workflows with shared authentication and Fetch-API modules
- ✅ **Role-Based Access Control** with BCrypt security
- ✅ **DTO-based API** - entities never cross the controller boundary
- ✅ **Comprehensive Error Handling** with centralized exception management
- ✅ **Test suite that runs without a real database** - `@SpringBootTest` uses an
  in-memory H2 instance, not the production MySQL datasource

---

## Architecture Overview

### Technology Stack
- **Framework**: Spring Boot 2.7.14
- **Java Version**: 17 or newer (verified with Java 21)
- **Database**: Laragon MySQL 8.x or compatible MySQL/MariaDB with enhanced schema
- **Security**: Spring Security + BCrypt + JWT
- **Frontend**: HTML5, CSS3, Vanilla JavaScript (Fetch API) - split static role pages, no Bootstrap, no frontend build step
- **Build Tool**: Maven

### Core Components

#### Entities (with New Fields)
```
User (users table)
├── id, username, password, role
├── email, phone, is_active ← NEW
└── created_at, updated_at

Student (students table)
├── id, user_id, full_name, class_name
├── cgpa, profile_image_path ← NEW
├── email, phone, enrollment_date, address ← NEW
└── created_at, updated_at

Teacher (teachers table)
├── id, user_id, full_name
├── profile_image_path ← NEW
├── email, phone, qualification ← NEW
├── years_of_experience, department ← NEW
└── created_at, updated_at

Grade (grades table)
├── id, student_id, course_id, grade_value
├── gpa_score ← NEW (BigDecimal for precise calculations)
├── remarks
└── created_at, updated_at

Course (courses table)
├── id, name, course_code, category_id, teacher_id
├── semester, credits ← NEW
└── created_at, updated_at
```

#### Services
- **AdminService**: Full CRUD with soft delete and restoration
- **StudentService**: Student profile management and CGPA calculations
- **TeacherService**: Teacher profile and course management
- **GradeService**: Grade recording and CGPA updates

#### Controllers
- **AdminController**: Complete admin endpoints for all entities
  - `/api/admin/users` - User CRUD
  - `/api/admin/students` - Student management + admit endpoint
  - `/api/admin/teachers` - Teacher management + hire endpoint
  - `/api/admin/statistics` - Dashboard statistics

#### Frontend

A split static frontend in `src/main/resources/static/`, served without a frontend
build step or framework. Each role has a dedicated HTML entrypoint and page script;
shared authentication behavior lives in `js/auth.js`.

- **index.html**: Student login and public portal entrypoint
- **dashboard.html**: Student dashboard
- **admin-dashboard.html**: Administrator workspace
- **teacher-dashboard.html**: Teacher workspace
- **js/auth.js**: Shared authentication and API helper behavior
- **service-worker.js**: Offline app-shell caching (network-first for `/api/**`,
  cache-first for the static shell)

The split frontend is the only supported browser architecture. Legacy page files
must either remain registered as supported entrypoints or be removed; they must not
be described as a retired unified application.

### LAN presentation deployment

For a same-network presentation, run the Spring Boot server directly. Do not serve
the repository directory through Laragon/Apache; that exposes source files and makes
the relative `/api` frontend calls target Apache instead of Spring Boot.

Before inviting testers, create an isolated MySQL database and account on this
computer. In Laragon's MySQL console, run the following once, replacing the
password with a strong local-only value:

```sql
CREATE DATABASE sis_wifi_test CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'sis_wifi_app'@'127.0.0.1' IDENTIFIED BY 'replace-with-a-local-test-password';
GRANT ALL PRIVILEGES ON sis_wifi_test.* TO 'sis_wifi_app'@'127.0.0.1';
```

In PowerShell, set the connection details and enable sample data only for this
disposable test database. The launcher refuses a LAN bind unless `DB_URL` points
to a local database whose name ends in `_test`, and the selected Wi-Fi network
is classified as **Private**:

```powershell
$lanIp = "192.168.1.25" # Replace with this computer's Wi-Fi IPv4 address.
$env:DB_URL="jdbc:mysql://127.0.0.1:3306/sis_wifi_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
$env:DB_USER="sis_wifi_app"
$env:DB_PASSWORD="the-local-test-password-you-created"
$env:UPLOAD_DIR="$PWD\uploads_wifi_test"
$env:SAMPLE_DATA_ENABLED="true"
.\run-sis.bat run $lanIp
.\ops\verify-lan-portal.ps1 -HostAddress $lanIp
```

Replace `<LAN-IP>` with the host's exact Wi-Fi IPv4 address. The launcher binds
to `127.0.0.1` by default; the explicit address is required for a LAN test. Only
use it on a trusted network whose Windows profile is **Private**, with an
isolated test database. The launcher verifies that the address belongs to a
local IPv4 interface and refuses it unless Windows classifies that interface's
network as **Private**. Open `http://<LAN-IP>:8080/index.html` on presentation
devices. Do not bind to `0.0.0.0`, expose port 8080 through a router, or test from
a Public Wi-Fi network.

New visitors should use **Apply for admission** from the login page. This
submits an application, not an immediately usable portal account. An
administrator must configure the test programme and academic year, review and
accept the application, and enroll the applicant before a student profile and
login are created. Provide the initial password securely; the student must
change it at first sign-in. Do not use the separate public `/api/auth/register`
endpoint for testers because it creates a student login without an academic
student profile.

### Remove Wi-Fi test data and keep one administrator

After the demonstration, return to the project root and keep the same local
test database connection variables (`DB_URL`, `DB_USER`, and `DB_PASSWORD`) in
the current PowerShell session. Ensure the Laragon `mysql.exe` client is on
`PATH` and the corresponding local MySQL server is running. Run:

```powershell
.\ops\reset-wifi-test-data.ps1 -DatabaseName sis_wifi_test
```

The script builds the application before changing data, requires you to type
the exact `_test` database name, clears only that schema's tables, reapplies
Flyway migrations, and creates/verifies exactly one active ADMIN account. It
prompts for the administrator username and a new password (minimum 12
characters) without placing the password on the command line. It then removes
only the project's `uploads_wifi_test` directory. The Flyway schema and
migration history remain; no sample teachers/students, admissions, or uploaded
test files remain. Never point this cleanup at `sis_db`, any shared database,
or a path other than the dedicated test upload folder.

Course catalog rules: every course has a unique institutional `course_code`, a
category of `SCIENCES`, `ARTS`, or `OTHER`, and positive `credits` representing
course units. Students see enrolled courses grouped by category; teachers see
category/code/unit metadata for assigned courses; administrators maintain these
fields when creating or editing courses. All portal roles can browse the public
course catalogue; administrators must create a course before adding its units.
Existing databases must run
`ops/migrate-course-catalog.sql` before startup with schema validation enabled.

For the complete Windows, Laragon, Docker, firewall, and same-Wi-Fi setup, see
[`docs/MIT-Student-Portal-Network-Setup-Guide.docx`](docs/MIT-Student-Portal-Network-Setup-Guide.docx).

---

## Database Setup

### Enhanced Schema Features

The `schema.sql` file includes:

1. **Soft Delete Support**
   - `is_active` flag on users table
   - Preserves academic history and audit trails

2. **New Columns for Advanced Features**
   - CGPA tracking (Decimal 4,2)
   - Profile image paths
   - Contact information (email, phone)
   - Academic details (qualification, experience)
   - Course credits for GPA weighting

3. **Comprehensive Indexes**
   - Performance optimization for common queries
   - Fast filtering and sorting

### Database Initialization

Flyway migrations under `src/main/resources/db/migration/` are the schema source
of truth in development and production. On a fresh database, Flyway applies the
baseline and forward migrations; Hibernate validates the resulting schema rather
than changing it. Development automatically baselines a non-empty legacy database
at V1 before applying later migrations. Back up existing data before first startup
with this migration-managed configuration. Production keeps automatic baselining
disabled unless an operator explicitly enables it for a reviewed adoption.

`schema.sql` is retained as a MySQL snapshot for manual inspection and legacy
initialization; new schema changes belong in Flyway migrations.

For a fresh local install, create the empty database, then start the app:

```sql
CREATE DATABASE sis_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

The default local connection is Laragon MySQL at `127.0.0.1:3306`, database
`sis_db`, user `root`, with an empty password. Override `DB_URL`, `DB_USER`, and
`DB_PASSWORD` through environment variables when using another database.

### Access from devices on the same Wi-Fi network

The application listens on loopback (`127.0.0.1`) by default. For an intentional
presentation to other devices, use only a trusted Wi-Fi network whose Windows
network profile is **Private**. Find the host's Wi-Fi IPv4 address with `ipconfig`,
then pass that exact address to `run-sis.bat` (not `0.0.0.0`). The launcher adds
only that HTTP origin to `CORS_ALLOWED_ORIGINS` and sets `FRONTEND_URL` to that
same address for email links. Open `http://<LAN-IP>:8080/` from another device on
that trusted network. Keep MySQL local and do not expose the development server
to public Wi-Fi or the internet.

Only on a trusted Private network, if Windows Firewall blocks the presentation, run
this command from an elevated PowerShell window to add the narrow private-profile rule:

```powershell
New-NetFirewallRule -DisplayName "MIT Student Portal - LAN 8080" -Direction Inbound -Action Allow -Protocol TCP -LocalPort 8080 -Profile Private
```

Keep the network profile private and do not forward port `8080` from the router to the
internet. Set `SERVER_PORT` if another port is needed.

### Where to change the network IP

You normally do **not** put the IP address inside the HTML or JavaScript. Start the
application bound to the host's exact Wi-Fi IPv4 address, and allow only that exact
origin:

```powershell
$lanIp = "192.168.1.25" # Replace with the current Wi-Fi IPv4 address.
.\run-sis.bat run $lanIp
```

Find the current host IPv4 address with `ipconfig`, then open
`http://<LAN-IP>:8080/` on the phone or other computer. Use the same address in
`SERVER_ADDRESS`, the CORS allow-list, and `FRONTEND_URL`; the launcher configures
these for the explicit interface address. Do not use this setup
while Windows classifies the Wi-Fi as Public; stay on localhost until it is a
trusted Private network.

The binding defaults are stored in
`src/main/resources/application-dev.properties` as `server.address` and
`server.port`. The frontend API calls are relative (`/api`), so no frontend IP edit
is required when the network changes.

---

## API Endpoints Documentation

### User Management

```
GET    /api/admin/users              - List all active users
GET    /api/admin/users/{id}         - Get user by ID
POST   /api/admin/users              - Create new user
PUT    /api/admin/users/{id}         - Update user
DELETE /api/admin/users/{id}         - Soft delete user
POST   /api/admin/users/{id}/restore - Restore deleted user
```

### Student Management

```
GET    /api/admin/students                  - List all students
GET    /api/admin/students/{id}             - Get student by ID
POST   /api/admin/students/admit            - Admit new student
PUT    /api/admin/students/{id}             - Update student profile
DELETE /api/admin/students/{id}             - Remove student
GET    /api/admin/students/{id}/cgpa        - Get student CGPA
POST   /api/admin/students/{id}/calculate-cgpa - Recalculate CGPA
```

### Teacher Management

```
GET    /api/admin/teachers        - List all teachers
GET    /api/admin/teachers/{id}   - Get teacher by ID
POST   /api/admin/teachers/hire   - Hire new teacher
PUT    /api/admin/teachers/{id}   - Update teacher profile
DELETE /api/admin/teachers/{id}   - Remove teacher
```

### Course Management

```
GET    /api/admin/courses         - List all courses
POST   /api/admin/courses         - Create a course (assign a teacher, optionally enroll students)
GET    /api/teacher/my-courses    - Courses taught by the logged-in teacher
GET    /api/teacher/courses/{courseId}/students - Roster for a course assigned to the logged-in teacher
```

### Dashboard

```
GET /api/admin/statistics         - Dashboard statistics
GET /api/admin/deleted-users      - List soft-deleted users for recovery
POST /api/reports/export          - Export a report (requires institutional report-view permission)
```

Student CGPA, transcripts, and academic reports use approved grades only. Pending grades stay in the teacher/admin review workflows and are not shown as finalized student results.

---

## Admin Dashboard Usage

### Features

1. **Dashboard Tab**
   - Real-time statistics
   - Quick links to manage entities
   - Soft-deleted user count

2. **Students Tab**
   - View all students with CGPA
   - Admit new students
   - Edit student profiles
   - View/calculate CGPA
   - Remove students (soft delete)

3. **Teachers Tab**
   - View all teachers
   - Hire new teachers
   - Edit teacher profiles
   - Remove teachers

4. **Users Tab**
   - Manage all system users
   - Create admin, teacher, or student accounts
   - Restore deleted users
   - View user status

### Form Validation

All forms include:
- Real-time field validation
- Required field checking
- Email format validation
- Phone number validation
- Password strength requirements
- Error message display

### Performance Monitoring

The dashboard tracks:
- Form validation time (ms)
- Submission time (ms)
- Total processing time
- Error counts per form

**Download Reports As:**
- JSON format (for data analysis)
- CSV format (for spreadsheets)
- HTML format (for viewing/printing)

---

## Security Features

### Authentication
- JWT token-based authentication
- BCrypt password hashing (applied consistently at the point of creation - including
  the public self-registration endpoint, which previously stored passwords in plain text)
- Public self-registration (`/api/auth/register`) always creates a STUDENT account;
  ADMIN/TEACHER accounts can only be created by an authenticated admin via
  `POST /api/admin/users`

### Authorization
- Role-based access control (ADMIN, TEACHER, STUDENT)
- Method-level security with `@PreAuthorize`, plus coarse path-based role gates in
  `SecurityConfig` (`/api/admin/**`, `/api/teacher/**`, `/api/student/**`) as
  defense-in-depth
- Self-service ownership checks (`StudentAccessService`/`TeacherAccessService`) let a
  student/teacher access their own report or upload their own profile photo without
  needing the broader ADMIN authority

### Data Protection
- Soft delete preserves sensitive data
- User soft-delete restoration
- Every controller works in DTOs, never JPA entities - responses can't accidentally
  leak internal fields (password hash, JPA proxy state) that a raw entity would carry
- File access (`FileUploadService`) resolves every path against the upload root and
  rejects anything that would escape it (path traversal protection)
- CORS is centralized in `config/WebConfig.java` with an explicit allowed-origins list
  (`application.properties` → `cors.allowed-origins`), not a per-controller wildcard

---

## CGPA System

### Current implementation and policy boundary

```
CGPA = Sum(GPA_Score * Course_Credits) / Total_Credits
```

The application calculates credit-weighted CGPA from approved grades only.
Final reports are versioned and remain unavailable to students until an
administrator approves and separately publishes the current results version.
Changing a result invalidates approval for the changed version. See the
[academic results workflow](docs/ACADEMIC_RESULTS_WORKFLOW.md) for endpoints,
roles, and release checks.

The grade-to-GPA values currently retained in code are legacy compatibility
values, not a confirmed Makerere Institute of Technology policy. Do not use
them as an official scale until the institution approves the exact grade
boundaries, grade points, rounding, and credit rules.

### Result approval and publication

Student-facing academic results, CGPA, transcripts, and report downloads are
locked until an administrator approves and publishes the current results
version. Approval records a versioned snapshot; publication is a separate
administrator action. Changes to any grade after approval change the result
version and require approval and publication again. Approval and publication
events are recorded in the audit log, and students receive an in-app
notification only after publication.

Course assessment components and marks are stored separately. Component
weights are course-specific, must total exactly 100%, and missing marks remain
missing rather than counting as zero. Retakes are represented as separate
grade attempts linked to the original attempt; no attempt is automatically
selected to replace another.

The system does not infer mark-to-letter-grade bands, pass thresholds, retake
treatment for GPA/credits, or academic-standing criteria. The assessment
calculation service refuses to assign a final letter grade without configured
grade boundaries, and CGPA finalization is blocked when retake attempts are
present until the institutional retake policy is configured. Academic standing
therefore remains not finalized.

---

## File Upload Configuration

### Configuration

Edit `application.properties`:

```properties
# File upload directory (Windows)
file.upload.directory=C:/uploads/

# Or for Linux/Mac
file.upload.directory=/uploads/

# Maximum file size (10MB)
file.upload.max-size=10485760
```

### Profile Image Storage

- Student profile images: `C:/uploads/students/`
- Teacher profile images: `C:/uploads/teachers/`

---

## Soft Delete Implementation

### What is Soft Delete?

Soft delete marks records as inactive without permanently deleting them. This:
- Preserves academic history and audit trails
- Allows data recovery
- Maintains referential integrity
- Prevents data loss from accidental deletions

### How It Works

1. **User Deactivation**
   - User's `is_active` flag set to `false`
   - User cannot login
   - Associated student/teacher becomes inactive

2. **Student Removal**
   - Student's user account marked inactive
   - Grade and attendance records preserved
   - Can be restored later

3. **Teacher Removal**
   - Teacher's user account marked inactive
   - Course assignments preserved
   - Can be restored later

### Recovery

```
POST /api/admin/users/{id}/restore
```

---

## Exception Handling

### Global Exception Handler

All errors are handled consistently:

1. **Validation Errors** (400 Bad Request)
   - Field-level error messages
   - HTTP 400 response

2. **Authentication Errors** (401 Unauthorized)
   - Invalid credentials
   - Expired tokens

3. **Access Denied** (403 Forbidden)
   - Insufficient permissions
   - Role restrictions

4. **Resource Not Found** (404 Not Found)
   - Missing entities
   - Invalid IDs

5. **Server Errors** (500 Internal Server Error)
   - Unexpected exceptions
   - Database errors

### Error Response Format

```json
{
  "statusCode": 400,
  "message": "Validation failed",
  "timestamp": "2024-06-02T10:30:00",
  "path": "/api/admin/students",
  "validationErrors": {
    "fullName": "Full name is required",
    "className": "Class name cannot be empty"
  }
}
```

---

## Deployment Checklist

Before pushing to production, use the maintained
[release checklist](docs/RELEASE_CHECKLIST.md) and
[deployment guide](docs/DEPLOYMENT_GUIDE.md). Review the
[integration status and remaining work](docs/INTEGRATION_GAPS.md) for provider,
monitoring, storage, and notification connections that have not been verified.
The checklist below is a basic development reminder, not production sign-off.

- [ ] Supply unique JWT and payment webhook secrets through the approved secret manager.
- [ ] Configure and verify the dedicated production MySQL account and private endpoint.
- [ ] Configure persistent upload storage and verify backup/restore access.
- [ ] Configure password policies
- [ ] Set up SSL/TLS for HTTPS
- [ ] Create admin user account
- [ ] Test all CRUD operations
- [ ] Verify soft delete functionality
- [ ] Test CGPA calculations
- [ ] Review error logs
- [ ] Performance test with expected load

---

## Performance Optimization

The system includes:

1. **Database Indexes** on commonly queried fields
2. **Connection Pooling** for efficient DB access
3. **Lazy Loading** for relationships
4. **Client-side Caching** for performance data
5. **Efficient Queries** with proper JPA usage

---

## Code Documentation

Every file includes:
- **Class-level comments**: Purpose and responsibility
- **Method-level comments**: What the method does, parameters, return values
- **Field comments**: Purpose of each field
- **Complex logic comments**: Explaining business rules

### Example

```java
/**
 * Calculate CGPA for student based on all their grades
 * CGPA = Sum(GPA * Credits) / Total Credits
 * Uses BigDecimal for precise calculations
 * 
 * @param studentId the student's unique identifier
 * @return calculated CGPA as BigDecimal
 * @throws RuntimeException if student not found
 */
public BigDecimal calculateCGPA(Long studentId) {
    // Implementation...
}
```

---

## Monitoring & Reporting

### Performance Reports

Generate reports via the admin dashboard:

**JSON Report**
```json
{
  "formName": "admitStudentForm",
  "validationTimeMs": "45.23",
  "submissionTimeMs": "123.45",
  "totalTimeMs": "168.68",
  "errorCount": 0,
  "timestamp": "2024-06-02T10:30:00Z"
}
```

**CSV Report**
```
Form Performance Report
Form Name,admitStudentForm
Validation Time (ms),45.23
Submission Time (ms),123.45
```

### Logging

Configure logging in `application.properties`:

```properties
logging.level.com.sis=DEBUG
logging.pattern.console=%d{yyyy-MM-dd HH:mm:ss} - %msg%n
```

---

## Maintenance & Updates

### Adding New Features

1. Create entity with `@Entity` annotation
2. Create repository extending `JpaRepository`
3. Create service with `@Service` and `@Transactional`
4. Create controller with `@RestController`
5. Add frontend HTML/CSS/JS
6. Update schema.sql for new tables

### Database Migrations

For changes to existing schema:
1. Update entity classes
2. Modify schema.sql
3. Update JPA configuration if needed
4. Run database scripts

---

## Demo Credentials

When the app starts against an empty database, `SampleDataLoader` seeds demo accounts automatically. The application log intentionally does not print passwords, even for seed/demo data, so they're documented here instead:

| Role | Username | Password |
|------|----------|----------|
| Admin | `admin` | `admin123` |
| Teacher | `teacher1` / `teacher2` | `teacher123` |
| Student | `student1` ... `student5` | `student123` |

Change these before deploying anywhere reachable outside your own machine - `SampleDataLoader` is a dev/demo convenience, not a production seeding strategy.

---

## Troubleshooting

### Common Issues

**1. Students not showing in dashboard**
- Check if user accounts are active (`is_active = 1`)
- Verify authentication token
- Check browser console for errors

**2. CGPA not calculating**
- Ensure grades have `gpa_score` values
- Check if course has `credits` assigned
- Verify decimal precision in database

**3. File uploads failing**
- Check directory permissions
- Verify path in `application.properties`
- Ensure directory exists

**4. Forms not validating**
- Check browser console for JavaScript errors
- Validation is native HTML5 constraint validation (`required`, `minlength`, etc. on
   each input). Check the page-specific script loaded by the relevant static page when
   diagnosing browser validation behavior.

---

## Support & Documentation

For detailed API documentation:
- Review `AdminController.java` comments
- Check entity class Javadoc
- Review service methods documentation

For frontend documentation:
For frontend documentation:
- See comments in `js/auth.js`, `js/dashboard-script.js`, `js/modules-script.js`,
  `js/admin-script.js`, and `js/teacher-script.js`.
- Design tokens are documented in the active stylesheets under `css/`.

---

## Version Information

- **Spring Boot**: 2.7.14
- **Java**: 17
- **MySQL**: 5.7+ / MariaDB 10.3+
- **Last Updated**: June 2026

---

## License & Usage

This system is proprietary to your organization. The application includes:
- Comprehensive error handling
- Security best practices
- Performance optimizations
- Complete documentation

Production readiness depends on completing and evidencing the target-environment
configuration, security, integration, migration, monitoring, backup, recovery,
and acceptance requirements in the release checklist. Do not treat this README
or a successful local test as production approval.
