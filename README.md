# Makerere Institute of Technology Student Information Portal

The MIT Student Information Portal is a Spring Boot application with a static,
responsive web interface for students, employees, administrators, applicants,
and public visitors. The application serves its own frontend and API; no
separate JavaScript framework build is required.

## Current deployment

The Render service is a **test environment**, not a production-ready student
service. On 24 November 2026, Render reported deployment
`dep-dc3117h5efls739467gg` live on image/commit
`sha-a418a654694006fdadb432cb06ce25161455a8ab`. Render reports
`Deploy succeeded | Live`. The account-security page, its MFA stylesheet and
script, and service worker returned HTTP 200; the deployed assets include the
hidden-state fix and cache v39. Public actuator health requests timed out from
this environment. Authenticated sign-in, MFA enrollment/recovery, and email
delivery have not been tested.
See [Security hardening and release checks](docs/SECURITY_HARDENING.md) for
the evidence and remaining target-environment checks.

The homepage is a portal gateway:

- `/` — Student, Employee, and Guest choices.
- `/index.html` — existing student sign-in.
- `/admin-login.html` — employee and administrator sign-in.
- `/public/index.html` — public institutional information.

The tested Render Free instance uses `UPLOAD_DIR=/tmp/uploads`. This storage is
ephemeral: uploaded files may be lost when the service restarts or redeploys.
Use only approved test accounts and data there. Confirm the Render service is
connected to a separate test database before using or entering non-test data.
For production, provision persistent upload storage, verified backup and
restore procedures, and institution-approved operating controls first.

## Technology

- Java 25 and Spring Boot 4.1.2; Maven Wrapper builds the application.
- Spring MVC, Spring Security, JPA/Hibernate, Flyway, and MySQL-compatible
  databases. The current migrations target MySQL; Render PostgreSQL is not a
  drop-in replacement.
- Static HTML, CSS, and vanilla JavaScript under
  `src/main/resources/static/`, with the MIT logo used as the favicon across
  the portal pages.
- JWT-backed authentication, role- and ownership-checked APIs, refresh-token
  revocation, rate limiting, and a service worker/PWA.
- One active session per user, with session revocation controls; automatic
  required-course registration for eligible students when admission is
  completed, a term registration window opens, or the Registrar updates a
  programme semester; registrar-reviewed
  progression recommendations; queued notification-email retries; scheduled
  fee/grade reminders and attendance-risk alerts; finance approval for new fee
  charges; and configurable admission-document requirements.
- Local H2-based tests, disposable MySQL migration validation, JavaScript unit
  tests, and Playwright browser tests run in CI.

## Run locally

Use JDK 25. For the direct Windows setup, configure a supported MySQL 8.x
database and local environment, then follow
[Local Java and PWA hosting](docs/LOCAL_JAVA_AND_PWA_HOSTING.md). The Maven
Wrapper is included; installing Maven separately is unnecessary.

To run the Windows Docker Compose workflow:

```powershell
Copy-Item .env.example .env
# Edit .env and replace all required placeholders.
.\run-sis.bat docker-up
```

Stop the stack with `.\run-sis.bat docker-down`. A full database-volume reset
is destructive; review the prompt and ensure required backups exist before
confirming it.

For manual startup, follow [IDE setup](docs/IDE_SETUP.md) and
[deployment configuration](docs/DEPLOYMENT_GUIDE.md). Never commit `.env`
files, credentials, database exports, or uploaded student data.

## Verify changes

```powershell
.\mvnw.cmd -q test
npm ci
npm run build
npm run test:unit
```

The full Playwright suite runs in GitHub Actions against an isolated application
and test database. For release evidence and the latest deployment record, see
[Release checklist](docs/RELEASE_CHECKLIST.md) and
[Remaining work and deployment](docs/REMAINING_WORK_AND_DEPLOYMENT.md).

## Render deployment

For a step-by-step installation, administration, security, and recovery
handbook, see the [Complete Setup and Operations Guide](docs/MIT_STUDENT_PORTAL_COMPLETE_SETUP_OPERATIONS_GUIDE.pdf)
and its [editable HTML source](docs/MIT_STUDENT_PORTAL_COMPLETE_SETUP_OPERATIONS_GUIDE.html).
The guide was reviewed against the repository and recorded deployment on
20 November 2026; verify live settings and approvals before operational use.
For synthetic admin-only sampling steps, use the
[Admin Sampling Sample Data PDF](docs/MIT_STUDENT_PORTAL_ADMIN_SAMPLING_SAMPLE_DATA.pdf)
or its [editable HTML source](docs/MIT_STUDENT_PORTAL_ADMIN_SAMPLING_SAMPLE_DATA.html).
The sample data is for a disposable test database only.
For a concise five-minute Student Information System overview, see the
[presentation PDF](docs/MIT_SIS_FIVE_MINUTE_PRESENTATION/MIT_SIS_Five_Minute_Presentation.pdf),
[editable slide source](docs/MIT_SIS_FIVE_MINUTE_PRESENTATION/index.html), and
[PNG slide exports](docs/MIT_SIS_FIVE_MINUTE_PRESENTATION/images/).

GitHub Actions runs backend, migration, frontend, browser, and image-build
checks before publishing Docker Hub's `latest` and commit-specific tags. The
current deployment uses
`docker.io/ciphernelson/mit-portal:sha-e7a6f502caed9e180e9f1451ed267f030ae4d2af`.
Render Free may sleep while idle. The current test deployment's uploads are
temporary; do not assume the paid Blueprint's persistent disk is attached to
this Free service.

See [Render deployment](docs/RENDER_DEPLOYMENT.md) for the current test-service
details, required database configuration, deployment flow, and production
gates. The [security hardening release checks](docs/SECURITY_HARDENING.md)
cover the required CSP, CSRF, upload, and privileged-MFA verification. The
root `render.yaml` describes the paid persistent-disk configuration;
`render.free.yaml` is a separate Free test-service template. Neither file
provisions an external MySQL database or supplies secret values.

## Security and production-readiness boundaries

- The Free Blueprint enables MFA and requires it for privileged roles, while
  keeping universal enrollment optional. The active image-based service's
  settings must still be verified; follow [MFA recovery](docs/MFA_RECOVERY.md)
  before enabling broader access.
- Configure and verify the institution-approved email sender before relying
  on transactional mail or account recovery.
- The public pages still require institution-approved admissions, contact,
  privacy, retention, and official social-profile information. See
  [privacy, retention, and recovery](docs/PRIVACY_RETENTION_AND_RECOVERY.md).
- Payment provider code does not establish live provider acceptance.
  Follow [payment configuration](docs/PAYMENT_CONFIGURATION.md) and keep
  unverified channels disabled.
- Local backup scripts do not prove a successful restore or provide off-site
  retention. Complete a restore rehearsal for the target host before real-data
  use.

For the architectural and workflow review, see
[integration gaps](docs/INTEGRATION_GAPS.md),
[role workflow gaps](docs/ROLE_WORKFLOW_GAPS.md), and
[academic results workflow](docs/ACADEMIC_RESULTS_WORKFLOW.md).
