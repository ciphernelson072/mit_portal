# University MIS Implementation Plan

> **Status note (26 October 2026):** This document preserves the incremental
> implementation history and original roadmap; it is not a current acceptance
> checklist. The source now includes result snapshot approval/publication and
> course-specific assessment/retake data (migrations V41-V42), plus the
> administrator-authorized correction workflow (V43). V1-V43 and Hibernate
> validation pass on disposable MySQL 8.0; the TiDB pilot is pending V43
> rollout after a fresh backup. Institutional grading/retake policies and
> target-environment acceptance remain open.
> See [Academic Results Workflow](./ACADEMIC_RESULTS_WORKFLOW.md) and the
> [Release Checklist](./RELEASE_CHECKLIST.md) for current status.

This repository is being upgraded incrementally from a Student Information Portal
to a University Management Information System. Existing role-specific pages and
API routes remain supported while new bounded modules are introduced.

## Completed Foundation Slice

- Added `Campus`, `Faculty`, `Department`, `Program`, `AcademicYear`, and
  `AcademicTerm` entities.
- Added repositories for the institutional hierarchy and academic periods.
- Added authenticated read-only catalog endpoints under `/api/institution`.
- Added the same tables to `schema.sql`.
- Added the forward migration at
  `src/main/resources/db/migration/V2__institution_structure.sql`.
- Verified compilation and `GradeApprovalWorkflowTest` (6 tests passing).

## Completed Registration Slice

- Added term-based course sections and student registration records.
- Added administrator section creation under `/api/registration/sections`.
- Added administrator academic-term creation/listing with validation that term
  dates fall within the selected year, plus a term-specific section creation
  and capacity-listing panel in Academic Operations.
- Added admin controls to open or close an existing term's registration window;
  both section browsing and registration mutations honor the updated state.
- Added student section lookup, registration, active-registration listing, and
  drop endpoints.
- Academic enrollment checks now recognize active section registrations as
  well as legacy course rosters, so exam-permit requests and academic actions
  do not reject students using the new registration workflow.
- Hall-ticket exam lookup includes exams for registered section courses while
  retaining legacy roster access and excluding dropped registrations.
- Restricted section catalogue access to students and authorized registration
  or academic operators; student requests still receive only eligible sections.
- Fee creation and fee listing now use the dedicated `FINANCE_FEE_MANAGE`
  permission, separate from payment confirmation and other finance operations,
  and are available in the permission-aware operations workflow.
- Grade-approval queue access and approve/reject actions now support the
  dedicated `GRADE_APPROVAL_MANAGE` permission; administrator access remains
  supported, and teacher grade submission still requires course ownership.
- Recipient lookup and bulk email sending now support the dedicated
  `EMAIL_CAMPAIGN_MANAGE` permission, while the message-page campaign panel
  appears only after the server confirms that authority.
- Accommodation managers with `ACCOMMODATION_MANAGE` can now also read the
  room catalogue required for their workflows; student room browsing remains
  available.
- Operations workflow visibility is derived only from authenticated server
  authorities; local storage role hints no longer reveal privileged workflows.
- Added a student academics-page panel for viewing registrations and eligible
  sections, registering, and dropping courses during an open registration
  window.
- Student section listings now show live registered-seat counts and remaining
  capacity, and disable registration actions for full sections.
- The student timetable refreshes after course registration or drop so schedule
  changes appear without a full page reload.
- New timetable entries are scoped to an academic term in Academic Operations;
  registration conflict checks compare term-specific entries only within that
  term while retaining legacy unscoped schedule entries.
- New timetable API requests require an academic term, preventing unscoped
  schedule creation from bypassing term-scoped locking and conflict checks;
  historical unscoped entries remain readable.
- Timetable day values are validated against the seven recognized weekdays and
  normalized to uppercase before storage.
- Timetable publishing rejects overlapping bookings for the same venue on the
  same weekday and term; back-to-back periods and bookings in other terms remain
  allowed. The academic term is locked during the check-and-save operation.
- Registration now rejects overlapping same-day timetable entries against the
  student's active registrations for the term; touching start/end boundaries
  remain valid non-overlapping periods.
- Student timetable results now include courses from active section
  registrations as well as legacy course enrollments, so newly registered
  courses appear in the student's timetable.
- Timetable lookup matches each term-scoped schedule to the exact active
  course-registration pair instead of cross-combining every course and term.
- Registration-backed timetable entries are limited to the active academic
  year's terms that have not ended, preventing registrations retained from
  past terms from appearing indefinitely.
- Term-scoped timetable entries are now matched to each exact active
  course-registration pair, preventing a course schedule from appearing for a
  term in which the student is not registered.
- Student section browsing only returns eligible sections while the academic
  term's registration window is open; the registration mutation continues to
  enforce the same rule independently.
- Section creation trims section codes and validates the 30-character database
  limit at the request boundary, avoiding whitespace inconsistencies and
  database-level truncation errors.
- Included section and term names/codes in registration API responses so the
  student panel can display readable course and section details.
- Enforced open-term, active-program, capacity, duplicate-course, and active-drop
  rules in `RegistrationService`.
- Added `V4__course_sections_and_registration.sql` and updated `schema.sql`.
- Section codes are unique per course and academic term, allowing common
  labels such as section A to be reused across different courses in the same
  term; migration V35 updates the original term-wide constraint.

## Completed Curriculum Slice

- Added program curriculum course mappings and course prerequisites.
- Added administrator curriculum and prerequisite management endpoints.
- Added student curriculum and prerequisite lookup endpoints.
- Registration now rejects courses whose prerequisites lack an approved passing
  grade (`gpaScore >= 2.00`). Courses without configured prerequisites remain
  compatible with existing registration behavior.
- Added `V5__curriculum_and_prerequisites.sql` and updated `schema.sql`.

## Completed Examination Publication Slice

- Added publication state and publication timestamp to exam results.
- Newly entered results remain unpublished until an administrator publishes them.
- Student result lookup now returns published results only.
- Added administrator publication endpoint at
  `/api/academic/exam-results/{id}/publish`.
- Existing legacy results remain visible when the V6 migration adds the column.
- Added `V6__exam_result_publication.sql` and updated `schema.sql`.

## Completed Academic Records Slice

- Added approved-grade-only transcript data under `/api/academic-records`.
- Added transcript PDF download using the managed PDF document service.
- Added degree audit against required curriculum courses.
- Added graduation eligibility reporting based on completed required courses with
  passing approved grades.
- Existing broad staff reporting remains unchanged for compatibility.

## Completed Finance Control Slice

- Added auditable student financial holds with reason, creator, timestamps, and
  release state.
- Added administrator hold creation and release endpoints.
- Added student active-hold visibility endpoint.
- Registration now rejects students with an active financial hold.
- Added `V7__financial_holds.sql` and updated `schema.sql`.

## Completed Payment Attempt Slice

- Added pending payment attempts with provider references.
- Added partial-payment and outstanding-balance validation.
- Added server-side administrator confirmation before marking fees paid.
- Added student payment-attempt history.
- Added `V8__payment_attempts.sql` and updated `schema.sql`.
- The existing legacy full-payment endpoint remains available for compatibility;
  it remains administrator-only and must not be used to treat an unverified
  provider callback as a confirmed payment.

## Completed Allocation and Waiver Slice

- Confirmed payment attempts now create one auditable payment allocation.
- Added administrator-approved fee waivers with amount limits.
- Payment attempts account for active waivers when validating outstanding balance.
- Waivers and allocations can settle a fee without changing the original charge.
- Added `V9__payment_allocations_and_waivers.sql` and updated `schema.sql`.

## Completed Provider and Refund Slice

- Added HMAC-verified provider callback endpoint at
  `/api/public/finance/payments/callback`.
- Added externalized `PAYMENT_WEBHOOK_SECRET` configuration.
- Provider success callbacks confirm pending attempts; failure callbacks close
  them as failed.
- Added administrator refund processing with amount and duplicate-refund guards.
- Refunded amounts can reopen a fee balance while preserving the original payment
  and allocation records.
- Added `V10__payment_refunds.sql` and updated `schema.sql`.

## Completed Reconciliation Slice

- Added bank statement reconciliation entries keyed by provider reference.
- Added exact amount matching against completed payment attempts.
- Added explicit `UNMATCHED` and `DISCREPANCY` states for finance review.
- Added administrator reconciliation entry import, filtering, and summary APIs.
- Added `V11__bank_reconciliation.sql` and updated `schema.sql`.

## Completed HR Foundation Slice

- Added general staff records linked to user accounts.
- Added staff self-service leave requests.
- Added administrator pending-leave review and finalization.
- Added date validation and overlapping pending-leave protection.
- Added admin HR onboarding and leave-review screens, plus staff leave request/history screens.
- HR profiles must be explicitly provisioned with the verified employee number, title, and hire date; creation is limited to active administrator or teacher accounts and rejects duplicate account/employee numbers.
- Added `V12__hr_staff_and_leave.sql` and updated `schema.sql`.

## Completed Library Slice

- Added library catalog items with ISBN and copy availability.
- Added administrator issue and return circulation actions.
- Added student loan history.
- Added automatic overdue marking when loan history is read.
- Added pessimistic stock/loan locking so concurrent issues cannot borrow beyond available copies and concurrent returns restore a copy only once.
- Added `V13__library_catalog_and_loans.sql` and updated `schema.sql`.

## Completed Accommodation and Transport Slice

- Added hostel rooms with capacity and available-bed tracking.
- Added an authorized pending-application queue so accommodation staff can identify application IDs before allocating rooms.
- Added administrator allocation of open hostel applications.
- Added student hostel-allocation lookup with building and room details in the accommodation dashboard.
- Prevented multiple active room assignments for the same student, including simultaneous allocations from separate applications, by locking the student record.
- Hostel applications transition to `ALLOCATED` after successful assignment; other open applications for the same student are closed and removed from the pending queue.
- Transport renewal now rejects duplicate currently valid passes, marks expired passes as `EXPIRED`, and serializes concurrent renewals by locking the student's record.
- Added `V14__accommodation_and_transport_controls.sql` and updated `schema.sql`.

## Completed Scholarship Lifecycle Slice

- Added administrator scholarship creation, opening/closing, application listing, and final review endpoints.
- Scholarship applications are rejected while the opportunity is closed, with concurrent submissions serialized on the scholarship record.
- Repeated submissions return the existing application instead of creating duplicates, including after an opportunity has closed.

## Completed Campus Event Registration Slice

- Students may register only before the event starts; retries of an existing registration remain idempotent, including after the event starts.
- Students can cancel and re-register before the event starts; event-row locking serializes concurrent registration/cancellation updates.

## Completed Procurement and Asset Slice

- Added suppliers and active supplier catalog.
- Added procurement requests with submitted, approved, rejected, ordered, and
  closed states.
- Added administrator procurement review workflow.
- Added asset register with unique asset tags, purchase details, and assignment.
- Added `V15__procurement_and_assets.sql` and updated `schema.sql`.

## Completed Research and Postgraduate Slice

- Added research project proposals with supervisor assignment.
- Added administrator project approval and rejection.
- Added thesis submission records with review states.
- Added student ownership checks for projects and thesis submissions.
- Added administrator thesis review workflow.
- Wired supervisor selection to the active teacher directory and validate
  supervisor IDs against teacher profiles.
- Replaced client-entered thesis file paths with validated PDF uploads capped at
  the configured 10 MB limit and generated, student-scoped storage paths.
- Added student submission history and ownership-checked downloads; research
  reviewers can download pending thesis PDFs without exposing storage paths.
- Added `V16__research_and_postgraduate.sql` and updated `schema.sql`.

## Completed Quality and Accreditation Slice

- Added program learning outcomes with active catalog lookup.
- Added accreditation records, evidence paths, validity dates, and status.
- Added administrator accreditation review workflow.
- Added authenticated program outcome and accreditation lookup endpoints.
- Added `V17__quality_and_accreditation.sql` and updated `schema.sql`.

## Completed Permission Foundation Slice

- Added database-backed permissions and role-permission assignments.
- Preserved existing role authorities for backward compatibility.
- Active assignments are emitted as `PERM_*` authorities during authentication.
- Added administrator permission creation, assignment, revocation, and lookup APIs.
- Added `V18__database_permissions.sql` and updated `schema.sql`.

The standard permission catalog is created idempotently at startup, so
administrators can assign these permissions through `/api/permissions`.

## Completed Permission Migration Slice

- Finance operations accept their explicit grants:
  `FINANCE_HOLD_MANAGE`, `FINANCE_PAYMENT_CONFIRM`, `FINANCE_REFUND`,
  `FINANCE_RECONCILIATION`, and `FINANCE_WAIVER`.
- Each finance grant is limited to its matching operation; a refund grant does
  not authorize payment confirmation, reconciliation, holds, or waivers.
- `REPORTS_VIEW` grants institution-wide academic, class, teacher, comparison,
  and top-student reports without granting other administrator operations.
- Admissions-review access is covered by integration tests for delegated
  application review and denial of unrelated procurement access.
- Procurement-management access is covered by integration tests for delegated
  supplier lookup and denial of unrelated HR and library operations.
- HR-management access is covered by integration tests for delegated staff and
  leave administration and denial of unrelated library and accommodation operations.
- Library-management access is covered by integration tests for delegated
  catalog creation and denial of unrelated HR and accommodation operations.
- Procurement, research review, accreditation review, HR, library,
  accommodation, admissions, and course-section management accept their
  corresponding catalog permissions.
- Service-request review uses `SERVICE_REQUEST_MANAGE`.
- Curriculum and prerequisite writes use `REGISTRATION_MANAGE`.
- Exam-permit review uses `EXAM_PERMIT_MANAGE`; exam-result publication uses
  `EXAM_RESULT_PUBLISH`. Student permit requests and teacher result entry retain
  their existing role and ownership checks.
- `ADMIN` remains accepted for compatibility. Grade approval and rejection
  remain `ADMIN`-only to preserve separation of duties from teacher submissions.
- Accreditation review uses `QUALITY_REVIEW` consistently with outcome creation
  and the pending accreditation queue.
- Thesis review uses `RESEARCH_REVIEW` consistently with project review and the
  pending thesis queue.
- Role-permission lookups fetch the permission entity with each assignment so
  active grants can be safely loaded into authentication authorities.
- H2 integration tests cover delegated access and denial without a grant.
  Verification: Maven 41 tests and Playwright 25 tests passed on 2026-10-03.

## Completed Audit Slice

- Service-request status changes write `SERVICE_REQUEST_STATUS_UPDATED` audit
  records with the acting user, request ID, and previous/new status.
- Permission creation, role grants, and successful revocations record the
  acting administrator and permission details; no-op revocations create no event.
- Grade submissions, approvals, and rejections record the actor and grade ID;
  pending-state validation runs before audit creation.
- Administrator payment-attempt confirmation records the actor, attempt,
  provider reference, amount, and fee; provider callbacks remain a distinct path.
- Administrator refunds record the actor, refund ID, attempt, fee, amount, and
  reason after the fee balance is reconciled.
- Fee-waiver approvals record the actor, waiver ID, fee, amount, and reason
  after any fee settlement is applied.
- Financial-hold creation and release record the actor, hold ID, student ID, and
  reason/status transition in the same transaction.
- Reconciliation imports record the actor, entry ID, provider reference, amount,
  and resulting match status.
- Procurement supplier, request, and asset creation plus request approvals and
  rejections record the actor and operation details.
- Audit records and their related status mutations are saved in the same service transaction.
- H2 integration tests verify actor attribution and mutation details.
- Verification: Maven 49 tests and Playwright 25 tests passed on 2026-10-03.

Flyway is enabled in development and production, with Hibernate validation enabled
in both profiles. The V1 baseline and V2 onward migrations are applied at startup.
Development baselines a non-empty database at V1; production requires an explicit
operator decision before adopting an existing database. Back up and validate
existing data before enabling or changing migration history.

Continue migrating remaining controller guards from coarse roles to explicit
permissions, keeping special role and resource-ownership checks intact and adding
integration coverage for both delegated access and denial without a grant.

## Completed Admissions Slice

- Added admission application and student lifecycle entities, statuses, and
  repositories.
- Added public application submission and application-number status lookup under
  `/api/public/admissions`.
- Added administrator application listing and accept/reject decisions under
  `/api/admissions`.
- Added administrator enrollment of accepted applications into `StudentProgram`,
  with an initial `StudentStatusHistory` record.
- Added finalized-decision protection so accepted, rejected, and withdrawn
  applications cannot be decided again.
- Added an administrator lifecycle action for deferment, suspension,
  withdrawal, graduation, reactivation from deferment/suspension, and alumni
  progression. Changes target a specific student/programme/academic-year
  enrollment, validate allowed transitions, and write status history.
- Added a student programme lifecycle list and controls to the admin student
  management screen.
- Added programme transfer for an active source enrollment to a different active
  programme and the currently active academic year. The source enrollment is
  closed as `TRANSFERRED`, the destination enrollment is created as `ACTIVE`,
  and both changes are recorded in status history.
- Graduation eligibility now requires at least one required curriculum course,
  completion of every required course with an approved passing grade, an active
  programme directory, and no outstanding fees or active financial hold.
  Administrators cannot set an enrollment to `GRADUATED` until that exact
  programme/year passes the audit.
- Added programme/year-specific degree-audit requests and an admin student-list
  clearance view. It displays academic, directory, and financial blockers and
  only enables graduation approval when the server-side audit reports eligible.
- Linked student status-history entries to the specific programme enrollment
  where applicable, while retaining nullable compatibility for older history
  and student-wide events. Added `V34__link_student_status_history_to_program.sql`.
- Added an admin lifecycle history view with actor, timestamp, status, reason,
  and programme/year context. The endpoint returns the latest 100 changes and
  displays older student-wide records without enrollment metadata.
- Lifecycle status changes require a non-empty reason and authenticated
  administrator, so the audit record cannot be created with missing decision
  context.
- Lifecycle status changes and programme transfers lock the source enrollment
  while checking and applying transitions, preventing concurrent administrative
  actions from relying on stale status.
- Course add/drop now shares the term registration window and serializes
  student/section mutations with locks; a student cannot drop a course after
  the registration period closes.
- The student academics page now exposes the registration and drop workflows;
  the UI disables drops when the term is closed and the backend remains the
  authority for all registration-window and eligibility checks.
- Added `V3__admissions_and_student_lifecycle.sql` and updated `schema.sql`.
- Verified compilation and `GradeApprovalWorkflowTest` (6 tests passing).

## Implementation Order

### 1. Admissions and student lifecycle

Add under `com.sis.admissions` and `com.sis.students`:

- `Applicant`, `AdmissionApplication`, `AdmissionDocument`, `AdmissionDecision`
- `StudentProgram`, `StudentStatusHistory`, `StudentGuardian`
- Application, review, offer, acceptance, deferment, withdrawal, transfer, and
  graduation-clearance workflows.
- Pages: `admissions.html`, `admission-review.html`, `application-status.html`,
  `student-record.html`, and `graduation-clearance.html`.

The existing `Student.className` remains a compatibility field until
`StudentProgram` is populated and all consumers migrate to program, cohort,
academic year, and term references.

### 2. Academic catalog and registration

Add curriculum, prerequisites, course sections, instructors, rooms, schedules,
registration windows, course registrations, add/drop, withdrawal, and academic
holds. Refactor the current direct `Course.teacher` relationship into section
instructor assignments without breaking existing course endpoints.

### 3. Examinations and academic records

Add exam planning, moderation, grade-change requests, locked result publication,
transcripts, degree audit, academic probation, appeals, and graduation eligibility.
Preserve the existing pending/approved/rejected grade state machine.

### 4. Finance

Replace the boolean fee payment model with invoices, invoice items, payments,
payment allocations, refunds, waivers, sponsorships, financial holds, provider
callbacks, receipts, and reconciliation. Frontend payment requests must never be
treated as confirmed payments without a verified provider callback.

### 5. Operations

Add HR, library, accommodation, transport, procurement, inventory, assets,
research, postgraduate studies, quality assurance, and accreditation modules.

### 6. Platform hardening

- Replace the single `Role` enum with database-backed permissions and role grants.
- Add audit events for every privileged mutation.
- Add Flyway baseline and subsequent migrations after the existing schema is
  captured as `V1__baseline_schema.sql`.
- Add API pagination, filtering, error codes, request IDs, idempotency, and
  optimistic locking.
- Add CI, backup/restore tests, security scanning, integration tests, and
  Playwright acceptance tests.

## File Arrangement Target

New modules should be organized by feature rather than by technical layer:

```text
com.sis
├── shared
├── identity
├── institution
├── admissions
├── students
├── academics
├── registration
├── examinations
├── finance
├── humanresources
├── library
├── accommodation
├── transport
├── research
├── procurement
├── quality
├── reporting
└── integrations
```

Existing controllers and services should be moved one bounded module at a time;
compatibility endpoints should remain until frontend consumers have migrated.