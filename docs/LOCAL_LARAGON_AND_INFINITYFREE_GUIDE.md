# Local Laragon + IntelliJ and InfinityFree Frontend Guide

This project supports two deployment modes:

- **Local development:** IntelliJ IDEA runs Spring Boot and Laragon runs MySQL.
- **InfinityFree frontend:** InfinityFree serves the static HTML/CSS/JavaScript frontend while the same Java API runs on a publicly reachable HTTPS host or secure HTTPS tunnel.

InfinityFree cannot run Spring Boot, Java, MySQL, Flyway, SMTP, or payment webhooks. It also cannot access a private `localhost` API from a visitor's browser.

## Part A: Local Laragon + IntelliJ IDEA

### 1. Start MySQL in Laragon

1. Open Laragon.
2. Start **MySQL**.
3. Create a database named `sis_db` if it does not exist.
4. Confirm the local connection:

```text
Host: 127.0.0.1
Port: 3306
Database: sis_db
Username: root
Password: your Laragon MySQL password, often blank by default
```

The development profile defaults to:

```text
jdbc:mysql://127.0.0.1:3306/sis_db
```

### 2. Open the correct project

Open this folder in IntelliJ IDEA:

```text
C:\laragon\www\student_portal\mit_portal-main
```

Do not open only the parent `student_portal` folder as the Maven project.

### 3. Configure IntelliJ

Use:

- Project SDK: Java 17 or newer
- Maven wrapper: `mvnw.cmd`
- Main class: `com.sis.SisApplication`
- Active profile: `dev`

Set these environment variables only when your Laragon values differ:

```text
SPRING_PROFILES_ACTIVE=dev
DB_USER=root
DB_PASSWORD=
SERVER_PORT=8080
SERVER_ADDRESS=127.0.0.1
```

For local development, the frontend and API share the same origin:

```text
Frontend: http://localhost:8080
API:      http://localhost:8080/api
```

The default `src/main/resources/static/js/deployment-config.js` already uses `/api`.

### 4. Run from IntelliJ

Run `SisApplication` or use the Maven tool window:

```text
spring-boot:run
```

Open:

```text
http://localhost:8080/index.html
```

Local sample data is disabled by default. To use the local demo accounts, set:

```text
SAMPLE_DATA_ENABLED=true
```

Set a JWT secret before starting the application as well:

```powershell
$env:JWT_SECRET="replace-with-a-random-secret-of-at-least-32-characters"
```

Use demo data only locally. Never enable it in production.

### 5. Run from PowerShell instead

```powershell
cd C:\laragon\www\student_portal\mit_portal-main
.\run-sis.bat run
```

The Windows launcher reads local `MAIL_*` settings and configured backup marker
paths from `.env` without importing Docker database settings or printing
credentials. On startup it creates or reuses a fresh, checksum-verified local
database backup and uses `backups\.backup-success` for its status marker. It
uses the active local MySQL server's matching `mysqldump` and local development
database settings; failed backups remain visible as unhealthy instead of being
marked successful. Unless overridden, the upload backup marker is
`backups\.uploads-backup-success`; run `ops\backup-uploads.ps1` after uploads
change. This is the direct Windows/Laragon path; the Docker stack uses a
read-only backup mount and needs its own host-side or scheduled database backup.
If port 8080 is occupied, stop the old Java process or choose another port:

```powershell
$env:SIS_PORT="8081"
.\run-sis.bat run
```

Then open `http://localhost:8081/index.html`.

### 6. Local validation

```powershell
cd C:\laragon\www\student_portal\mit_portal-main
.\mvnw.cmd test
npx playwright test
```

The browser suite expects the running app at `http://127.0.0.1:8080`. For another port:

```powershell
$env:BASE_URL="http://127.0.0.1:8081"
npx playwright test
```

## Part B: Make the Local Java API Reachable to InfinityFree

The InfinityFree frontend cannot call `http://localhost:8080` because `localhost` means the visitor's own computer.

Choose one:

### Option 1: Recommended production arrangement

Run the Spring Boot API on a Java-capable HTTPS server with a public DNS name, for example:

```text
https://api.example.edu
```

Serve the frontend under the same institutional site, for example
`https://portal.example.edu`, or serve it directly from the API origin. Keep
Laragon/MySQL for local development. Production should use a secured MySQL
instance on the API host or a managed MySQL service, not a MySQL port exposed from
your laptop.

### Option 2: Temporary demo tunnel

Keep Java and Laragon running locally, then expose only the Spring Boot HTTP port through an HTTPS tunnel such as Cloudflare Tunnel or ngrok.

Example API URL:

```text
https://random-subdomain.example-tunnel.com
```

Do not expose port 3306. Only expose the Spring Boot API port.

A tunnel is for testing or demonstrations, not a dependable production deployment.

## Part C: Prepare InfinityFree Frontend

From the project root:

```powershell
cd C:\laragon\www\student_portal\mit_portal-main
.\ops\prepare-infinityfree.ps1 -ApiBaseUrl "https://api.example.edu/api"
```

The script creates:

```text
frontend-dist/
```

Upload the **contents** of `frontend-dist` to InfinityFree's:
```text
public_html/
```

The export script refuses to overwrite an existing output directory; remove the
previous generated bundle intentionally or choose another direct-child output
directory.

Do not upload the repository itself.

### What the script configures

It generates:

```text
frontend-dist/js/deployment-config.js
```

with:

```javascript
window.MIT_PORTAL_CONFIG = {
  apiBaseUrl: 'https://api.example.edu/api',
};
```

Never put database passwords, JWT secrets, SMTP passwords, or payment secrets in this file.

## Part D: Backend settings for the hosted frontend

The frontend and API must be same-site for authenticated browser sessions. The
current API enforces `SameSite=Strict` and rejects `SameSite=None` and `Lax`.
Different origins under one institutional site (for example `portal.example.edu`
and `api.example.edu`) are supported with HTTPS, an exact CORS allow-list, and an
Origin check on state-changing API requests. An unrelated InfinityFree subdomain
and API domain are not a supported authenticated deployment pair; use a custom
domain shared by both services or serve the frontend from the API origin.

```text
SPRING_PROFILES_ACTIVE=prod
DB_URL=your-production-mysql-jdbc-url
DB_USER=your-production-db-user
DB_PASSWORD=your-production-db-password
JWT_SECRET=long-random-secret-at-least-32-characters
PAYMENT_WEBHOOK_SECRET=long-random-secret-at-least-32-characters
UPLOAD_DIR=/secure/path/uploads
FRONTEND_URL=https://portal.example.edu
CORS_ALLOWED_ORIGINS=https://portal.example.edu
COOKIE_SECURE=true
COOKIE_SAME_SITE=Strict
MAIL_ENABLED=true
MAIL_HOST=your-smtp-host
MAIL_USERNAME=your-smtp-user
MAIL_PASSWORD=your-smtp-password
FLYWAY_ENABLED=true
DDL_AUTO=validate
```

`COOKIE_SECURE=true` and HTTPS are required for production. Keep
`COOKIE_SAME_SITE=Strict`; the startup validator rejects any other setting. The
Origin check uses the same exact origins configured in `CORS_ALLOWED_ORIGINS`.

## Part E: InfinityFree upload checklist

Upload only:

- `.html` files
- `css/`
- `js/`
- `icons/`
- `manifest.json`
- `service-worker.js`
- other static assets required by the pages

Do not upload:

- Java source
- `pom.xml`
- `mvnw` or `mvnw.cmd`
- `target/`
- `.env`
- database dumps
- `schema.sql`
- `sample_data.sql`
- `uploads/`

The repository `.htaccess` is for Apache directory protection and should not be uploaded unchanged if it denies all public access. InfinityFree's `public_html` must be able to serve `index.html` and its static assets.

## Part F: Verify the split deployment

1. Open the InfinityFree URL.
2. Confirm `index.html` loads.
3. In browser Developer Tools, confirm requests go to the configured API HTTPS origin.
4. Confirm `POST /api/auth/login` returns the correct CORS origin.
5. Confirm cookies are `Secure`, `HttpOnly`, and `SameSite=Strict`.
6. Confirm authenticated API requests include credentials.
7. Confirm `/api/auth/refresh` works.
8. Confirm logout clears both cookies.
9. Confirm the Java API cannot be reached over plain HTTP in production.

## Security actions before sharing the site

- Rotate the InfinityFree account password if it was visible in a screenshot or chat.
- Rotate all database, JWT, SMTP, payment, and tunnel credentials that were exposed.
- Never commit a populated `.env` file.
- Do not use demo passwords outside local sample data.
- Do not expose Laragon MySQL port 3306 to the public internet.
