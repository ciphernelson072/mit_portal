# Academic Results, Approval, and Publication

Last reviewed: 31 October 2026

This guide describes the result workflow implemented in the source tree. It
does not certify a live deployment. Migration V44 adds the five-point policy
version, course core-module flag, retake-required state, and programme award
type. Migration V45 persists administrator review acknowledgments against a
fingerprint of the exact assessment/result state. V43-V45 were applied and
verified on the TiDB pilot after a fresh backup. Live assigned-teacher
correction-to-republication acceptance remains separate from source and migration validation.

## Roles and lifecycle

1. An administrator opens the course's **Assessment structure** action and
   defines its components, maximum marks, component types, and weights through
   `PUT /api/admin/courses/{courseId}/assessment-components`. Weights must total
   exactly 100 percent. Components can be added or removed only before any
   component marks exist; marked structures are immutable to preserve
   historical meaning.
2. A teacher assigned to the course selects an enrolled student. The teacher
   workspace creates or reuses a draft through
   `POST /api/teacher/courses/{courseId}/students/{studentId}/grade`, loads the
   course-specific component list, and submits each score through
   `PUT /api/teacher/grades/{gradeId}/assessment-marks`. The server checks
   course ownership, component membership, duplicate inputs, mark bounds, and
   required components. A missing score is `null`, not zero.
3. The server computes the weighted mark only when every required component
   has a mark. Incomplete marks remain pending; invalid weights or scores are
   rejected. Complete marks are mapped to the five-point grade bands below,
   with grade points and retake state derived on the server.
4. An administrator opens **Review marks** in the grade queue before approving.
   The grade-approval button stays disabled until the administrator acknowledges
   a complete review. The server validates the data and records the reviewer,
   timestamp, and SHA-256 fingerprint of the assessment structure, marks, and
   calculated result. Approval repeats the validation and requires the exact
   reviewed fingerprint; any intervening change requires a fresh review. Direct
   API calls cannot bypass missing or inconsistent assessment data.
5. Academic report approval records a versioned snapshot. Publication is a
   separate administrator action against that exact results version. Student
   reports and official downloads are available only after the current version
   is published.
6. A material result change changes the results version. The old approval does
   not approve the new version; the corrected version must be reviewed,
   approved, and published again.
7. To correct an approved course grade, an administrator authorizes the change
   with a required reason. The system preserves the previous grade and
   assessment-mark snapshot. An assigned teacher may then update the result
   through the existing grade or assessment-mark workflow. The first change
   moves the grade to pending, immediately invalidating student access to the
   prior published version. The teacher submits the correction for the normal
   administrator grade approval; administrators must then approve and publish
   the resulting semester version again.

Before publication, students can see the course and workflow status for
submitted grades (awaiting review, approved but awaiting publication, or
returned for revision). This status-only view omits marks, grade values, GPA,
and return reasons. The endpoint returns no workflow rows after publication;
the existing published grade/report behavior remains unchanged. Students also
see course names with no result submission where available. Student grade APIs, transcripts, CGPA,
earned credits, academic standing, award classification, and PDF downloads
remain masked or denied until the current approved results version is
published. Semester GPA, earned credits, and academic standing are calculated
server-side; the browser renders those official values instead of deriving
them.

The teacher result-status feed reports blocked and invalidated states without
marks or return reasons. When the feed observes either state, it creates one
grade-category notification per assigned teacher, student, status, and result
version; repeated feed reads do not duplicate notifications. The grade-category
visibility preference controls whether the notification appears in a teacher's
in-app notification list.

Academic report endpoints:

- `GET /api/reports/academic/{studentId}/status` — workflow status.
- `GET /api/student/grade-status` — authenticated student's pre-publication
  course and workflow status only; empty after official publication.
- `GET /api/student/grades` — published grade details; empty until official
  publication.
- `POST /api/admin/grades/{gradeId}/review` — validate and record administrator
  acknowledgment of the exact pending assessment/result state.
- `POST /api/reports/academic/{studentId}/approve` — approve a results version.
- `POST /api/reports/academic/{studentId}/publish` — publish an approved
  version; the request must identify the version.
- `POST /api/admin/grades/{gradeId}/corrections` — authorize a correction for
  an approved grade. The request requires a reason; only an administrator with
  grade-review authority may call it.
- Student-facing report and official-download endpoints enforce publication
  checks server-side. Hiding a link in the UI is not the access-control
  boundary.

The endpoints that configure assessment structures, create retakes, approve
results, and publish official results require administrator authority. Teacher
draft creation and mark reads/writes check both the authenticated role and
course assignment; draft creation also verifies student enrollment. Audit
events record assessment submissions, review acknowledgments, retake creation,
approval, and publication. Automated role tests cover these boundaries, but
live TiDB acceptance remains a deployment gate.

## Correction workflow

The V43 correction record captures the authorization reason, actor, time, and
the prior grade plus assessment marks. Direct edits/deletes remain blocked
unless an administrator has authorized the correction; the assigned teacher
must use the normal submission and administrator approval workflow. Correction
authorization, start, submission, required changes, and completion are audited.
The correction does not itself publish results: a new semester snapshot must
be approved and published after the grade is approved.

## Assessments and retakes

Assessment components are course-specific; the system does not impose one
universal coursework/final-paper split. Each component stores its type, weight,
maximum score, and whether a mark is required. Scores are kept separately from
the grade attempt so the detailed coursework layout can show component-level
marks.

Retakes are separate grade attempts. `attempt_number` increments and
`original_attempt_id` links the retake to the original grade; the original
attempt is retained. Creating a retake does not decide which mark replaces
another in GPA, CGPA, or earned credits.

## Five-point grading and progress rules

The backend centralizes the supplied Ugandan tertiary five-point standard in
`UgandanTertiaryGradingPolicy` (`UG_5_POINT_V1`). Marks are percentages; the
lower bound is inclusive, and a decimal mark belongs to the band beginning at
that threshold (for example, 79.99 is B+ and 50.00 is D).

| Percentage mark | Grade | Grade points | Description |
|---|---:|---:|---|
| 80-100 | A | 5.0 | Exceptional / Distinction |
| 75-<80 | B+ | 4.5 | Excellent |
| 70-<75 | B | 4.0 | Very Good |
| 65-<70 | C+ | 3.5 | Good |
| 60-<65 | C | 3.0 | Fair |
| 55-<60 | D+ | 2.5 | Satisfactory |
| 50-<55 | D | 2.0 | Pass (minimum passing grade) |
| 0-<50 | F | 0.0 | Fail / Retake Required |

The minimum course pass mark and point value are 50% and 2.0. Scores below
50% are persisted with `retake_required=true`. Approval rejects a mark/grade/
grade-point/retake-state mismatch. Grade labels outside this scale (including
legacy A-/B-/C-/D- labels) cannot be submitted as current-policy grades.
Course prerequisites and required-course completion in the degree audit use
the same minimum passing grade point of 2.0.

Normal Progress (NP) requires CGPA >= 2.00 and an approved passing result for
every registered core course. Probationary Progress (PP) applies when CGPA is
below 2.00 or any approved core result is below 2.00. If core results are
missing, unpublished, or use an unverified legacy scale, standing is
`NOT_FINALIZED`; the student endpoint masks it until the current semester
result version is officially published.

Final award classification is calculated only when the latest student
programme has an explicit award type (`BACHELOR` or `DIPLOMA`) and an eligible,
published CGPA:

| CGPA | Bachelor's Degree | Diploma |
|---|---|---|
| 4.40-5.00 | First Class (Honours) | Distinction / Class I |
| 3.60-<4.40 | Second Class - Upper Division | Credit / Class II (Upper) |
| 2.80-<3.60 | Second Class - Lower Division | Credit / Class II (Lower) |
| 2.00-<2.80 | Pass (General Degree) | Pass / Class III |
| <2.00 | Fail / No Award | Fail |

New programme setup requires an award type. Administrators can assign one to
existing programmes in Institution Setup; unclassified programmes do not
receive an award classification.

Migration V44 marks previously stored rows `LEGACY_UNVERIFIED` unless their
existing letter grade is on the five-point scale; rows with percentage marks
are re-derived using the new bands. Canonical letter-only legacy rows get the
new points but have no source percentage to verify against. Previously stored
unsupported labels remain unverified and block CGPA finalization until an
authorized correction/revalidation. Existing approval/publication snapshots
must be reviewed and republished after migration where the results version
changes.

## Student grade appeals

Migration V51 adds an audited appeal record linked to a grade. A student can
appeal only their own approved grade while the result is in the currently
published official report. Only one active appeal is allowed per grade; a new
appeal may be submitted after the previous one is decided.

The staff workflow requests consultation with the assigned teacher. The teacher
may submit a written response, or staff may record that consultation occurred
without one. An administrator with grade-approval permission records the final
decision only after consultation is recorded. Students and staff can follow
appeal status; the teacher queue is scoped to assigned courses. Appeal
approval records an outcome for review and does not change marks or authorize
a correction. Any grade change must still follow the separate administrator
correction-authorization, teacher update, reapproval, and republication path.

The specified policy does not define which retake attempt contributes to
CGPA, credit replacement/earning on a retake, attempt limits, or an official
CGPA rounding rule. The existing implementation currently reports a
credit-weighted CGPA rounded to two decimal places using `HALF_UP`; this
implementation detail is not represented as an institution-approved rounding
rule. Retake-attempt treatment remains blocked rather than guessed: approved
retake attempts continue to block CGPA finalization until the institute defines
the attempt-treatment rule. Correction and result-reopening procedure, along
with appeal deadlines and escalation, still require institutional sign-off.
The appeal implementation is local source only until target-database
validation and release gates pass.

## Required validation before release

- V41-V44 and V51 were applied with Hibernate schema validation on disposable
  MySQL 8.0 and TiDB v8.5.0 on 11 November 2026; the complete current set of
  53 migrations also passed on both disposable engines. Verify data-preserving
  migration of a reviewed intended-database copy before any production
  migration.
- Test teacher ownership, administrator-only configuration/approval/publication,
  missing marks, zero marks, duplicate components, invalid weights, out-of-range
  scores, retake history, changed results after approval, and unpublished
  report/PDF access.
- Verify V44 on the target database, exercise the admin correction control and
  assigned-teacher update/submission, confirm the old report is locked, and
  approve and republish the resulting version before official use.
- Verify V51 against a reviewed TiDB data-preserving copy, then exercise
  student submission, teacher consultation, administrator decision, and the
  separate grade correction workflow against the intended database topology.
- Configure and verify each programme's award type and each course's core
  designation. Confirm the remaining retake-attempt treatment before accepting
  CGPA/credits involving retakes as final.
- See the [release checklist](./RELEASE_CHECKLIST.md) and
  [integration inventory](./INTEGRATION_GAPS.md) for deployment acceptance
  evidence.
