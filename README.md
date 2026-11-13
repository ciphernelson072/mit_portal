# Makerere Institute of Technology Student Information Portal

The MIT Student Information Portal is a Spring Boot application with a static,
responsive web interface for students, employees, administrators, applicants,
and public visitors. The application serves its own frontend and API; no
separate JavaScript framework build is required.

## Current deployment

The Render service is a **test environment**, not a production-ready student
service. Its homepage is a portal gateway:

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
  `src/main/resources/static/`.
- JWT-backed authentication, role- and ownership-checked APIs, refresh-token
  revocation, rate limiting, and a service worker/PWA.
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

The existing Render service uses the published Docker image
`docker.io/ciphernelson/mit-portal:latest`. GitHub Actions runs backend,
migration, frontend, browser, and image-build checks before publishing that
tag from the configured branches. Render Free may sleep while idle. The current
test deployment's uploads are temporary; do not assume the paid Blueprint's
persistent disk is attached to this Free service.

See [Render deployment](docs/RENDER_DEPLOYMENT.md) for the current test-service
details, required database configuration, deployment flow, and production
gates. The root `render.yaml` describes the paid persistent-disk configuration;
`render.free.yaml` is a separate Free test-service template. Neither file
provisions an external MySQL database or supplies secret values.

## Security and production-readiness boundaries

- The Free Blueprint defaults MFA enrollment and privileged-role enforcement
  off. Verify the active Render settings; follow [MFA recovery](docs/MFA_RECOVERY.md)
  before enabling enforcement.
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
