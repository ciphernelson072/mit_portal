# Admin, Teacher, and Student Workflow Gap Audit

Last reviewed: 3 November 2026

This audit compares implemented source paths for the Admin, Teacher, and Student roles. It does not treat a UI control or recorded browser test as proof of production readiness.

## Summary

| Priority | Gap | Roles affected |
|---|---|---|
| High | Admin correction and retake actions exist, and teacher correction status is visible, but the student has no grade appeal/request workflow. | All roles |
| High | Report publication notifies the student, but the teacher workflow has no result-publication or returned-grade notification. | Teacher, Student |
| High | Academic grading still has legacy/admin import paths alongside the assessment-component workflow; ownership and publication effects need one complete end-to-end acceptance matrix. | All roles |
| Medium | Attendance disputes now support teacher review and administrator escalation, but routine absent/late notifications and a separate admin review of attendance marking remain undefined. | Teacher, Student, Admin |
| Medium | Student grade access remains entirely hidden until report publication, so ordinary grade feedback cannot reach students earlier even if policy allows it. | Teacher, Student |
| Medium | Announcements support role targeting, but not programme/course targeting or per-user notification preferences. | All roles |

## Confirmed gaps

### 1. Grade rejection notification

- **Files:** [GradeApprovalService.java](../src/main/java/com/sis/service/GradeApprovalService.java), [GradeApprovalController.java](../src/main/java/com/sis/controller/GradeApprovalController.java), [teacher-script.js](../src/main/resources/static/js/teacher-script.js)
- **Status:** The rejection path now creates an in-app notification for the submitting teacher, including the grade, course, and rejection reason, in addition to notifying the student.
- **Remaining:** Validate the notification in the current backend/browser test run and decide whether teacher email or an explicit return-to-draft action is required.

### 2. Correction lifecycle is incomplete across roles

- **Files:** [CourseAssessmentController.java](../src/main/java/com/sis/controller/CourseAssessmentController.java), [admin-script.js](../src/main/resources/static/js/admin-script.js), [teacher-script.js](../src/main/resources/static/js/teacher-script.js), [ACADEMIC_RESULTS_WORKFLOW.md](ACADEMIC_RESULTS_WORKFLOW.md)
- **Evidence:** Admin UI authorizes corrections and creates retakes. Teachers have a correction queue and receive authorization notifications; students can view correction status. There is no student grade appeal/request workflow or correction expiry.
- **Affected roles:** All roles.
- **Impact:** Students cannot initiate a review request or follow an appeal lifecycle.
- **Recommendation:** Decide whether to add student appeal submission/status and correction expiry; preserve the existing teacher queue and reapproval/republication controls.

### 3. Result-publication transitions do not cover teachers in the workflow

- **Files:** [AcademicReportService.java](../src/main/java/com/sis/service/AcademicReportService.java), [ReportingController.java](../src/main/java/com/sis/controller/ReportingController.java), [teacher-script.js](../src/main/resources/static/js/teacher-script.js)
- **Evidence:** Publication creates a student notification and can require a student account. The teacher dashboard has no publication status feed or notification for publication completion, blocked publication, or required corrections.
- **Affected roles:** Teacher and Student.
- **Impact:** Teachers cannot independently confirm whether results remain draft, approved, published, or invalidated after a correction.
- **Recommendation:** Add course/student publication summary to teacher views and notify the submitting teacher when a published version is changed or reopened.

### 4. Legacy and assessment grading entry can coexist

- **Files:** [AdminController.java](../src/main/java/com/sis/controller/AdminController.java), [TeacherController.java](../src/main/java/com/sis/controller/TeacherController.java), [CourseAssessmentController.java](../src/main/java/com/sis/controller/CourseAssessmentController.java), [teacher-script.js](../src/main/resources/static/js/teacher-script.js), [admin-script.js](../src/main/resources/static/js/admin-script.js)
- **Evidence:** Grade import and legacy grade endpoints remain alongside weighted assessment-component endpoints. Admin import can create grades without the same component, review, fingerprint, or publication safeguards.
- **Affected roles:** All roles.
- **Impact:** Identical UI/API concepts can produce grades with different audit and publication guarantees.
- **Recommendation:** Make one canonical grading workflow. If import remains necessary, route it through the same validation, review fingerprint, approval, correction, and publication controls, and label it clearly as a controlled migration/import operation.
- **Status:** Still open; existing import and legacy entry points need an explicit migration-only policy or equivalent assessment/review safeguards.

### 5. Attendance exception workflow

- **Files:** [teacher-script.js](../src/main/resources/static/js/teacher-script.js), [TeacherController.java](../src/main/java/com/sis/controller/TeacherController.java), [AttendanceController.java](../src/main/java/com/sis/controller/AttendanceController.java)
- **Evidence:** Students can dispute an attendance record; assigned teachers review course disputes, and administrators can review or handle escalated disputes. Teachers still submit attendance directly, with no separate administrator approval state for the original attendance marking.
- **Affected roles:** Teacher, Student, Admin.
- **Impact:** Students may not receive a proactive notification when marked absent or late, and the institution has not defined whether normal attendance submissions need a second review.
- **Recommendation:** Decide whether to notify students on absent/late marks and whether to require a separate administrator review of attendance submissions.
- **Implementation status:** Implemented in source with attendance-linked student disputes, requested corrected presence, teacher course queues, administrator escalation/review, notifications, audit logs, and migration V47. Fresh compilation and migration execution remain required.
- **Access-control note:** The shared dispute-list endpoint now scopes teacher results to assigned courses; administrator and attendance-manager results remain institution-wide.

### 6. Students receive no provisional or rejected-grade details

- **Files:** [StudentController.java](../src/main/java/com/sis/controller/StudentController.java), [modules-script.js](../src/main/resources/static/js/modules-script.js), [GradeApprovalService.java](../src/main/java/com/sis/service/GradeApprovalService.java)
- **Evidence:** Student grades are replaced with an empty list until a report is officially published. `modules-script.js` locks final grades, GPA, credits, and transcript in unpublished states.
- **Affected roles:** Student and Teacher.
- **Impact:** Students cannot distinguish unpublished, pending, approved, published, or rejected grades. A policy decision is therefore embedded in one broad visibility gate.
- **Recommendation:** Define institutional visibility policy per state; return a safe status-only DTO before publication, and expose approved feedback only when policy permits.

### 7. Announcement audience targeting

- **Files:** [AnnouncementController.java](../src/main/java/com/sis/controller/AnnouncementController.java), [AnnouncementController.java](../src/main/java/com/sis/controller/AnnouncementController.java)
- **Evidence:** Announcements now support `ALL`, `ADMIN`, `TEACHER`, and `STUDENT` audiences; in-app notifications and email recipients are filtered by role. Cancelling the audience prompt now cancels publication rather than defaulting to `ALL`.
- **Affected roles:** All roles.
- **Remaining:** Audience selection currently targets all users or one role; programme/course targeting and per-user notification preferences remain unavailable.
- **Impact:** Programme- and course-specific announcements remain unsupported, and delivery summary/deduplication are not established.
- **Recommendation:** Add narrower target IDs only if required; verify announcement delivery behavior in the supported deployment and test the prompt-cancellation path.

## Additional release-critical gaps requiring acceptance evidence

These are documented in the existing integration and deployment reports and need live acceptance rather than assumed implementation:

1. Flutterwave sandbox checkout, webhook retry, duplicate handling, settlement reconciliation, and refunds.
2. Production MySQL end-to-end workflows for login, admission provisioning, payments, reports, uploads, role ownership, and notifications.
3. Admin review of student service/support requests with complete lifecycle timestamps and escalation ownership.
4. Email bounce/retry monitoring and notification preferences for grade, CGPA, and announcement events.
5. Production backup scheduling, encryption, off-host retention, restore rehearsal, and upload durability.
6. MFA policy, recovery, key rotation, and locked-account support.
7. Shared-rate-limit storage and shared/object file storage before horizontal scaling.

## Recommended implementation order

1. Fix teacher rejection notification and add correction state visibility.
2. Create one canonical assessment/import/approval/publish workflow.
3. Add student appeal and attendance-dispute workflows with complete audit trails.
4. Implement teacher publication and correction dashboards.
5. Add notification preferences and audience-targeted announcements.
6. Run MySQL-backed end-to-end role tests and verify publication/download ownership.
7. Complete provider sandbox and production operational acceptance.
