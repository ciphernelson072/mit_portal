Review the project in this workspace and continue the production-ready role and endpoint cleanup.

The admin must be the supervisory role above teacher and student.

Re-enforce the backend role hierarchy in Spring Security, ensure public shell pages are not exposed, clean UI placeholder data from the dashboards, standardize the frontend-to-backend API calls, and harden ownership checks for student, teacher, and file access.

Implement the fix across the relevant Java and static frontend files, especially SecurityConfig, the auth scripts, the admin/teacher/student route controllers, and the dashboard templates. Keep the frontend as a UI convenience layer only. Use localStorage only for non-sensitive UI hints; never as access control. Add production-safe email configuration and route contract consistency improvements. Update all affected files and validate that ADMIN supervises both TEACHER and STUDENT, while TEACHER access to student data is limited to assigned courses.

Do not rely on any fake or hardcoded role assumptions. Keep all role checks server-side. Ensure student access is restricted to student-only data, teacher access is restricted to teacher-only and assigned student data, and admin access remains the supervisory control over both layers.

Also clean all static placeholder dashboard content and remove not-wired demo data from the final app state.

Relevant files to inspect and fix:
- src/main/java/com/sis/security/SecurityConfig.java
- src/main/java/com/sis/security/StudentAccessService.java
- src/main/java/com/sis/security/TeacherAccessService.java
- src/main/java/com/sis/security/FileAccessService.java
- src/main/java/com/sis/controller/FileUploadController.java
- src/main/java/com/sis/controller/AuthController.java
- src/main/java/com/sis/controller/StudentController.java
- src/main/java/com/sis/controller/TeacherController.java
- src/main/java/com/sis/controller/AdminController.java
- src/main/java/com/sis/controller/PortalController.java
- src/main/java/com/sis/controller/CampusServiceController.java
- src/main/java/com/sis/controller/ServiceRequestController.java
- src/main/resources/static/js/auth.js
- src/main/resources/static/js/script.js
- src/main/resources/static/js/admin-script.js
- src/main/resources/static/js/teacher-script.js
- src/main/resources/static/js/modules-script.js
- src/main/resources/static/js/dashboard-script.js
- src/main/resources/static/dashboard.html
- src/main/resources/static/admin-dashboard.html
- src/main/resources/static/teacher-dashboard.html
- src/main/resources/static/admin-login.html
- src/main/java/com/sis/service/EmailService.java
- src/main/resources/application.properties

The final output should be a production-ready role-aware, connected portal with no unconnected demo content and a strict backend-only authorization model.
