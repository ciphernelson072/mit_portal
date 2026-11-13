# Production Role & Integration Correction Report

## Scope
This project was reviewed against the attached product video and the actual portal source files. The findings show that the platform has a partially refactored security model, but several role checks and frontend-to-backend integrations were still not fully coherent.

## Executive Summary
The main issue is not a broken database schema. The main issue is consolidation and authorization drift:

- Admin, teacher, and student roles are defined, but the role hierarchy is not consistently enforced.
- Frontend screens still contain demo content and partial placeholders.
- Some pages are permitted publicly even though they are role-protected dashboards.
- UI logic relies on localStorage as a pseudo-auth mechanism, which is not a trusted security boundary.
- Several pages may appear connected, but they are not systematically wired to the real backend contract.

## Production-Ready Direction
The admin must be the supervisory role over the teacher and the student role.

```
ADMIN > TEACHER
ADMIN > STUDENT
```

Teachers are not granted the broad STUDENT authority: student self-service remains
student-owned. Teacher access to student records is scoped to their assigned courses.

The backend must enforce this hierarchy in Spring Security and keep the frontend as a UX convenience only.

## Major Corrective Changes Already Applied

### 1) Admin-over-teacher-student hierarchy
Added role hierarchy in:
- `src/main/java/com/sis/security/SecurityConfig.java`

This ensures admin can inherit authority over teacher and student access paths.

### 2) Public shell cleanup
Removed broad public access to dashboard shell pages in:
- `src/main/java/com/sis/security/SecurityConfig.java`

This prevents dashboard pages from being freely opened before backend validation.

### 3) UI convenience logic
Updated session enforcement in:
- `src/main/resources/static/js/auth.js`

The UI now treats admin as the supervisory role while keeping the server as the real authorization boundary.

### 4) Staff login routing
Updated staff login flow in:
- `src/main/resources/static/admin-login.html`

Admin and teacher staff login now redirect correctly to the appropriate staff portal.

## Remaining Recommendations for Full Production completion

### A. Role enforcement in the backend
- Keep role hierarchy in `SecurityConfig` as the primary policy.
- Maintain `@PreAuthorize` on controllers as the effective access control layer.
- Use admin privilege to supervise teacher and student operations without allowing the admin to be treated as a student or teacher in the wrong UI context.

### B. Frontend cleanup
- Remove all static demo data from role pages.
- Ensure every dashboard page loads from a real API contract and not from hardcoded placeholders.
- Keep `localStorage` only for user display convenience.

### C. Ownership hardening
- Review and harden:
  - `src/main/java/com/sis/security/StudentAccessService.java`
  - `src/main/java/com/sis/security/TeacherAccessService.java`
  - `src/main/java/com/sis/security/FileAccessService.java`
  - `src/main/java/com/sis/controller/FileUploadController.java`

### D. Route contract consistency
- Review and standardize route usage in:
  - `src/main/resources/static/js/admin-script.js`
  - `src/main/resources/static/js/teacher-script.js`
  - `src/main/resources/static/js/modules-script.js`
  - `src/main/resources/static/js/dashboard-script.js`

### E. Production readiness
- Configure real email provider for:
  - `src/main/java/com/sis/service/EmailService.java`
- Validate configs in:
  - `src/main/resources/application.properties`

## File Priority Order for Next Full Remediation Pass
1. `src/main/java/com/sis/security/SecurityConfig.java`
2. `src/main/java/com/sis/security/StudentAccessService.java`
3. `src/main/java/com/sis/security/TeacherAccessService.java`
4. `src/main/java/com/sis/security/FileAccessService.java`
5. `src/main/java/com/sis/controller/FileUploadController.java`
6. `src/main/resources/static/js/auth.js`
7. `src/main/resources/static/js/script.js`
8. `src/main/resources/static/js/admin-script.js`
9. `src/main/resources/static/js/teacher-script.js`
10. `src/main/resources/static/js/modules-script.js`
11. `src/main/resources/static/js/dashboard-script.js`
12. `src/main/resources/static/dashboard.html`
13. `src/main/resources/static/admin-dashboard.html`
14. `src/main/resources/static/teacher-dashboard.html`
15. `src/main/resources/static/admin-login.html`

## AI Handoff Prompt

> Review the project in this workspace and continue the production-ready role and endpoint cleanup. The admin must be the supervisory role above teacher and student. Re-enforce the backend role hierarchy in Spring Security, ensure public shell pages are not exposed, clean UI placeholder data from the dashboards, standardize the frontend-to-backend API calls, and harden ownership checks for student, teacher, and file access.
>
> Implement the fix across the relevant Java and static frontend files, especially SecurityConfig, the auth scripts, the admin/teacher/student route controllers, and the dashboard templates. Keep the frontend as a UI convenience layer only. Use localStorage only for non-sensitive UI hints; never as access control. Add production-safe email configuration and route contract consistency improvements. Update all affected files and validate that the security model is correct for admin > teacher > student.
