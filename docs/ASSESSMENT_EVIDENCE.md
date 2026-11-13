# Assessment Evidence Guide

Use this file as the presentation checklist. It maps common marking criteria to the implementation and the proof to show during assessment.

## Functional Evidence

| Criterion | Demonstration | Evidence location |
|---|---|---|
| Authentication | Log in as student, teacher, and administrator; show role-specific redirects. | `src/main/resources/static/index.html`, `admin-login.html`, `src/main/java/com/sis/controller/AuthController.java` |
| Authorization | Attempt a protected endpoint with no cookie and with the wrong role; show `401`/`403`. | `src/main/java/com/sis/security/SecurityConfig.java`, `SecurityIntegrationTest` |
| Student services | Show courses, approved grades, CGPA, attendance, fees, profile, documents, and service requests. | `dashboard.html`, `modules-style.css`, `modules-script.js` |
| Teacher services | Show course roster, grade submission, approval submission, materials, assignments, and batch attendance approval. | `teacher-dashboard.html`, `teacher-script.js` |
| Admin services | Show user/student/teacher/course/category management, grade approval, reports, and file management. | `admin-dashboard.html`, `admin-script.js` |
| Grade workflow | Submit a grade as teacher, approve or reject it as administrator, then show CGPA recalculation. | `GradeApprovalController.java`, `GradeApprovalService.java`, `GradeCalculationService.java` |
| Persistence | Restart the application and show data remains in MySQL. | `db/migration/`, `application-dev.properties` |

## Quality Evidence

Run these commands from the project root and record the output date in the release checklist:

```powershell
.\mvnw.cmd test
.\mvnw.cmd -DskipTests package
npm ci
npx playwright install chromium
.\ops\run-e2e.ps1
```

For the MySQL schema integration check, use Docker/Testcontainers:

```powershell
.\mvnw.cmd -Pmysql-integration test
```

The Windows runner builds and starts an isolated H2/sample-data server before
running browser tests; it does not use a configured MySQL/TiDB database or the
portal running on the normal application port. The GitHub Actions workflow at
`.github/workflows/ci.yml` provides its own isolated test server on Linux.

## Screenshots to Capture

Save dated screenshots under `docs/screenshots/`:

1. Student login and public course catalogue.
2. Student dashboard at desktop width.
3. Student dashboard at mobile width.
4. Teacher dashboard with assigned courses.
5. Teacher grade submission and batch attendance approval with present/absent records.
6. Admin dashboard statistics.
7. Admin course/category management.
8. Admin grade approval queue.
9. Unauthorized access response or login redirect.
10. Health endpoint and LAN browser access.

## Presentation Order

1. State the problem: centralize student, teacher, and administrator academic workflows.
2. Explain the architecture: Spring Boot API, MySQL persistence, static role-based frontend, JWT cookies, and role authorization.
3. Demonstrate student login and one academic workflow.
4. Demonstrate teacher submission and administrator approval.
5. Show database persistence and the weighted approved-grade CGPA rule.
6. Show responsive mobile layout and LAN access from a second device.
7. Show automated test output, the release checklist, and the CI workflow.
8. Mention known boundaries honestly: production requires HTTPS, dedicated credentials, secret management, backups, and a reviewed migration process.

## Evidence Rules

- Do not claim a workflow is complete until it has been demonstrated or covered by an automated test.
- Do not present demo credentials as production credentials.
- Keep the browser console and Network panel available during final acceptance testing.
- Capture the exact host IPv4 address and port used for the presentation.
- Mark Docker, Testcontainers, backup/restore, and production HTTPS checks as pending until they have been run in their target environment.
