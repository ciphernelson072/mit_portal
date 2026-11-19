# Admin, Teacher, and Student Workflow Gap Audit

Last reviewed: 11 November 2026

This audit compares implemented source paths for the Admin, Teacher, and Student roles. It does not treat a UI control or recorded browser test as proof of production readiness.

## Summary

| Priority | Gap | Roles affected |
|---|---|---|
| High | Student grade appeals now have a student-to-teacher-to-administrator lifecycle; correction expiry and institutional procedure remain undefined. | All roles |
| High | Teachers have a status feed for pending, blocked, awaiting-publication, published, and invalidated results; blocked and invalidated states now create status-only notifications. | Teacher, Student |
| High | Two controlled grade-entry paths remain: teacher assessment-component grading for routine work and administrator imports for authorized batch/exception entry. | All roles |
| Medium | Attendance disputes support teacher review and administrator escalation; absent/late student alerts are implemented, and teacher submissions remain authoritative without extra admin approval. | Teacher, Student, Admin |
| Medium | Students receive course and workflow status before report publication; marks, grade values, and return reasons remain hidden. | Student |
| Medium | Announcements support role targeting, but not programme/course targeting or per-user notification preferences. | All roles |

## Confirmed gaps

### 1. Grade rejection notification

- **Files:** [GradeApprovalService.java](../src/main/java/com/sis/service/GradeApprovalService.java), [GradeApprovalController.java](../src/main/java/com/sis/controller/GradeApprovalController.java), [teacher-script.js](../src/main/resources/static/js/teacher-script.js)
- **Status:** Rejection creates in-app grade-category notifications for both the submitting teacher and the student, including the course and rejection reason. The teacher workflow test now asserts each recipient and message exactly. Rejection does not send email; email remains for approved grades under the existing preference gate.
- **Correction visibility:** Students can view correction status, reason, and authorization time in Academics. A source-backed browser test covers authorized and submitted states and verifies unsafe text is escaped.
- **Remaining:** Validate the notification preference/display behavior and correction lifecycle against the target deployment after the current source updates are released.

### 2. Grade appeals and correction lifecycle

- **Files:** [GradeAppealController.java](../src/main/java/com/sis/controller/GradeAppealController.java), [GradeAppealService.java](../src/main/java/com/sis/service/GradeAppealService.java), [modules-script.js](../src/main/resources/static/js/modules-script.js), [admin-script.js](../src/main/resources/static/js/admin-script.js), [teacher-script.js](../src/main/resources/static/js/teacher-script.js), [ACADEMIC_RESULTS_WORKFLOW.md](ACADEMIC_RESULTS_WORKFLOW.md)
- **Evidence:** Students can appeal only their own approved grades in currently published official results, with at most one active appeal per grade. Staff requests teacher consultation; the assigned teacher can respond in writing, or staff can record a consultation without a written response. An administrator with grade-approval permission records the final decision. Approval does not modify the grade or bypass the separate correction-authorization workflow.
- **Affected roles:** All roles.
- **Implementation status:** Implemented locally with migration V51, audit events, notifications, and student/teacher/admin interfaces. Focused persistence tests cover unpublished-grade denial, student ownership, duplicate active appeals, consultation, both written and no-written-response paths, decision gating, and the separation from grade correction. Source-backed Playwright tests exercise all three role interfaces and HTML escaping.
- **Verification:** V51 passed migration and Hibernate schema validation on disposable MySQL 8.0 and TiDB v8.5.0; source-backed role-flow browser coverage passed. Validate against the reviewed intended-database copy before rollout.
- **Remaining:** Institutionally define correction expiry, appeal deadlines/escalation, and the formal correction/reopening procedure.

### 3. Teacher result-publication status and notification transitions

- **Files:** [AcademicReportService.java](../src/main/java/com/sis/service/AcademicReportService.java), [TeacherController.java](../src/main/java/com/sis/controller/TeacherController.java), [teacher-script.js](../src/main/resources/static/js/teacher-script.js)
- **Evidence:** First publication creates an in-app notification for the student and each current teacher assigned to a course in the published snapshot. Notifications are grouped by teacher and include the relevant course codes; repeated publication of the same snapshot does not duplicate them. The teacher dashboard now has a status-only feed for students with grade records in assigned courses.
- **Affected roles:** Teacher and Student.
- **Impact:** Teachers can track result lifecycle states and receive notifications for blocked or invalidated results without receiving marks or return reasons.
- **Implementation status:** Successful-publication notifications and blocked/invalidated status notifications are implemented. Status notifications are deduplicated per teacher, student, state, and results version, including repeated status-feed reads. The feed limits course labels to assigned courses and does not expose marks or full reports. Grade-category visibility preferences continue to control in-app display.
- **Verification:** `AcademicReportApprovalPersistenceTest` verifies blocked and invalidated notifications and duplicate suppression; `TeacherControllerFetchTest` verifies the feed uses the authenticated teacher account. V52 and V53 passed disposable MySQL 8.0 and TiDB v8.5.0 migration and Hibernate schema validation.
- **Remaining:** Validate the migrations and role workflows against a reviewed copy of the intended database before deployment.

### 4. Legacy and assessment grading entry can coexist

- **Files:** [AdminController.java](../src/main/java/com/sis/controller/AdminController.java), [TeacherController.java](../src/main/java/com/sis/controller/TeacherController.java), [CourseAssessmentController.java](../src/main/java/com/sis/controller/CourseAssessmentController.java), [teacher-script.js](../src/main/resources/static/js/teacher-script.js), [admin-script.js](../src/main/resources/static/js/admin-script.js)
- **Evidence:** Administrator import remains a controlled pending-record path; direct teacher `POST /api/teacher/grades` and `PUT /api/teacher/grades/{id}` now return HTTP 410 with migration guidance. The supported teacher entry flow creates a draft and saves component marks; approval requires configured components, saved marks, a matching calculated total, and the normal review fingerprint. Grade deletion remains available only for unapproved drafts and is not an alternate edit path.
- **Affected roles:** All roles.
- **Impact:** Existing API clients using direct teacher grade entry must migrate; imported records still require component-mark completion by an assigned teacher.
- **Policy:** Administrator file and single-grade imports remain available for authorized batch or exceptional operational entry, not routine teacher marking. The component-based teacher workflow remains the canonical path for ordinary grading.
- **Safeguards:** File and single-grade imports preflight current-policy grade values, optional GPA consistency, student enrollment, and existing student-course attempts before writing. The authenticated administrator is recorded as submitter and audit actor. Imported grades remain pending until the assigned teacher records and submits assessment-component marks for normal review and approval. Existing student-course attempts require the controlled correction or retake workflow. Legacy teacher create/update endpoints return HTTP 410.
- **Verification:** `GradeImportServiceTest` (11 tests) checks import preflight, audit attribution, and actor propagation; `AcademicRecordServiceTest` (9 tests) asserts grade creation remains pending with its submitter recorded; the administrator-only grade review/import authorization integration test passed.
- **Remaining:** Validate this operating procedure against the target database and institution's records-handling controls.

### 5. Attendance exception workflow

- **Files:** [teacher-script.js](../src/main/resources/static/js/teacher-script.js), [TeacherController.java](../src/main/java/com/sis/controller/TeacherController.java), [AttendanceController.java](../src/main/java/com/sis/controller/AttendanceController.java)
- **Evidence:** Students can dispute an attendance record; assigned teachers review course disputes, and administrators can review or handle escalated disputes. Teachers still submit attendance directly, with no separate administrator approval state for the original attendance marking.
- **Affected roles:** Teacher, Student, Admin.
- **Policy:** Students receive in-app notifications when newly marked absent or late, or when attendance changes into one of those states. Unchanged roster resubmissions do not generate duplicate alerts. Teacher submissions remain authoritative; students retain the existing dispute path, and no separate administrator approval is added.
- **Impact:** Student notices use the `ATTENDANCE` category. The category appears in the notification list by default and can be hidden through the student's notification preferences. Alerts contain course and date, not teacher remarks.
- **Implementation status:** Implemented locally alongside attendance-linked student disputes, requested corrected presence, teacher course queues, administrator escalation/review, notifications, audit logs, and migration V47. Focused service tests verify absent/late alerts, transitions, and duplicate suppression on repeated submission. Target database acceptance and deployment remain pending.
- **Access-control note:** The shared dispute-list endpoint now scopes teacher results to assigned courses; administrator and attendance-manager results remain institution-wide.

### 6. Student pre-publication grade visibility

- **Files:** [StudentController.java](../src/main/java/com/sis/controller/StudentController.java), [StudentGradeStatusDto.java](../src/main/java/com/sis/dto/StudentGradeStatusDto.java), [modules-script.js](../src/main/resources/static/js/modules-script.js), [ACADEMIC_RESULTS_WORKFLOW.md](./ACADEMIC_RESULTS_WORKFLOW.md)
- **Policy:** Before publication, students may see course and workflow status only (awaiting review, approved but awaiting publication, or returned for revision). Marks, grade values, GPA, and return reasons are not exposed.
- **Evidence:** `GET /api/student/grade-status` scopes the query to the authenticated student and returns only course code, course name, and workflow status. It returns an empty list after publication; the existing grade endpoint continues to return detailed grades only after publication. Backend contract/role tests and source-backed browser tests cover the minimal payload, escaping, status labels, and published-view hiding.
- **Affected role:** Student.
- **Implementation status:** Implemented locally. Target-database acceptance and deployment remain pending.

### 7. Announcement audience targeting

- **Files:** [AnnouncementController.java](../src/main/java/com/sis/controller/AnnouncementController.java), [AnnouncementController.java](../src/main/java/com/sis/controller/AnnouncementController.java)
- **Evidence:** Announcements now support `ALL`, `ADMIN`, `TEACHER`, and `STUDENT` audiences; in-app notifications and email recipients are filtered by role. Cancelling the audience prompt now cancels publication rather than defaulting to `ALL`.
- **Affected roles:** All roles.
- **Implementation status:** Role-filtered in-app notices use a distinct `ANNOUNCEMENT` category. Students can hide or show that category through profile preferences; older four- and five-category settings keep their existing index meanings and default announcements to visible. Optional announcement email remains controlled separately by the existing email opt-in. Recipient records are selected once per publication.
- **Remaining:** Audience selection currently targets all users or one role; programme/course targeting is unsupported. Delivery deduplication and a recipient delivery summary are not established.
- **Impact:** Programme- and course-specific announcements remain unsupported, and delivery summary/deduplication are not established.
- **Recommendation:** Add narrower target IDs only if required; verify announcement delivery behavior in the supported deployment and test the prompt-cancellation path.

### 8. Email delivery and request-notification boundaries

- **Files:** [AuthController.java](../src/main/java/com/sis/controller/AuthController.java), [EmailService.java](../src/main/java/com/sis/service/EmailService.java), [AdminEmailCampaignService.java](../src/main/java/com/sis/service/AdminEmailCampaignService.java), [messages.js](../src/main/resources/static/js/messages.js), [INTEGRATION_GAPS.md](./INTEGRATION_GAPS.md)
- **Evidence:** Password-reset and email-verification resends return generic responses for known and unknown accounts, including when provider delivery fails; provider errors are logged server-side. The failed token-creation transaction rolls back. Administrator campaigns are sent individually to selected active accounts and report partial provider failures.
- **Implementation status:** Campaign delivery continues after an individual send failure. The UI marks partial delivery as an error and leaves only named failed recipients selected when the returned names map completely to the submitted selection. If names no longer map (for example, an account was renamed during delivery), all selections are cleared and the recipient list must be refreshed before retrying. Request and leave email is not enabled; their current lifecycle alerts are in-app.
- **Verification:** Two partial-campaign browser regressions pass: identified failures remain selected for retry, while an unmatched failed name clears all selections and prompts a list refresh.
- **Verification:** `SecurityIntegrationTest` verifies generic responses for known/unknown accounts and confirms failed provider delivery leaves no reset or verification token persisted.
- **Remaining:** Verify transactional recovery and campaign delivery using approved target-environment test accounts. Decide whether request and leave notifications should also send email before changing that policy.

## Additional release-critical gaps requiring acceptance evidence

These are documented in the existing integration and deployment reports and need live acceptance rather than assumed implementation:

1. Flutterwave sandbox checkout, webhook retry, duplicate handling, settlement reconciliation, and refunds.
2. Production MySQL end-to-end workflows for login, admission provisioning, payments, reports, uploads, role ownership, and notifications.
3. Admin review of student and teacher service/support requests records current ownership and an append-only status/assignment history; historical changes before V53 cannot be reconstructed. Service requests, staff leave, attendance disputes, grade appeals, and grade corrections use in-app notifications, not email; decide separately whether these events should use transactional email.
4. Email bounce/retry monitoring and notification preferences for grade, CGPA, and announcement events.
5. Production backup scheduling, encryption, off-host retention, restore rehearsal, and upload durability.
6. MFA policy, recovery, key rotation, and locked-account support.
7. Shared-rate-limit storage and shared/object file storage before horizontal scaling.

## Recommended implementation order

1. Define whether legacy grade imports are migration-only or an approved operational path.
2. Resolve remaining institutional grade-policy decisions, including correction expiry and appeal deadlines/escalation.
3. Validate Flyway V51-V53 and current role workflows against a reviewed copy of the intended database.
4. Add narrower announcement audience targeting only if programme/course targeting is required.
5. Run target-database end-to-end role tests and verify publication/download ownership.
6. Complete provider sandbox and production operational acceptance.
