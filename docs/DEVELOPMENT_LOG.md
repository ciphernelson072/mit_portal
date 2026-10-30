# SIS Refactor — Changelog

## Current source status — 27 October 2026

- Added migration V45 for persistent administrator assessment-review
  acknowledgments. Each acknowledgment stores the reviewer, time, and
  SHA-256 fingerprint of the exact result state; approval revalidates the
  fingerprint, so changed marks or result details require a new review.
- V43 provides administrator-authorized correction records with immutable
  prior grade and assessment-mark snapshots. V41 and V42 provide result
  snapshots, assessments, and linked retake attempts.
- Added weighted assessment calculation with strict 100% component weights,
  mark-range validation, and explicit missing-mark handling. Approved
  Ugandan tertiary five-point boundaries now drive final letter grades and
  grade points; weighted assessments are converted by the central policy
  service.
- Academic report snapshots require administrator approval and separate
  publication; the current results version gates student report and official
  download access.
- Legacy edits remain blocked unless an administrator authorizes a correction;
  the first teacher change locks student access, then requires normal grade
  approval and report reapproval/publication. The active TiDB pilot still needs
  V43-V45 after a fresh verified backup.
- Flutterwave hosted-checkout and server-side transaction verification code is
  present, but live provider acceptance and database migration validation
  remain pending.
- V1-V43 migration and Hibernate validation passed on a disposable MySQL 8.0
  database. The Testcontainers profile is blocked by the local Docker API
  mismatch; the TiDB V43 rollout remains outstanding.
- Latest Maven verification after V45: 328 tests with no failures, errors, or
  skips (MySQL/Testcontainers suite excluded). The isolated Playwright suite
  passed all 87 tests; all 10 frontend unit tests and JavaScript syntax checks
  passed. These checks do not certify live V44/V45 migrations or deployment
  readiness.
- V1-V43 migration and Hibernate validation passed on a disposable MySQL 8.0
  database. V44/V45 still need target-engine validation; TiDB rollout remains
  pending.
- Club directory member counts and joined-state lookups now use bounded batch
  queries instead of per-club database queries.
- Teacher assignment feeds now follow the teacher's currently assigned courses,
  including assignments created by administrators, and assignment creation
  consistently records the course's assigned teacher.
- Teacher attendance approval now uses the combined legacy/section-registration
  course roster and loads existing attendance rows in one batch rather than one
  query per student.
- Course-material upload now honors the administrator access already granted
  by its endpoint policy while keeping teacher uploads restricted to their
  assigned courses.
- Teachers can now review submissions for their assigned course assignments;
  submission lookup is course-owner checked and returns student display names
  without exposing other courses' work. The submission API also rejects blank
  responses, matching the student form's validation. Student resubmissions now
  retain their existing policy but return HTTP 200 instead of 201, and the
  student submission view displays the saved response text.
- Service-request creation now selects only active administrator accounts for
  notification rather than loading every user account and filtering in memory.
- Requesters' service-request lists now use one owner-scoped, database-ordered
  query instead of two queries plus in-memory merge, de-duplication, and sort.
- Email-verification resend now relies on the normalized, case-insensitive
  email lookup directly; unknown addresses no longer trigger a full user-table
  scan while preserving the same non-disclosing response.
- Public and authenticated announcement feeds are consistently newest-first
  and safely label legacy announcements without an author as "System".

Senior-architect review and hardening pass. Grouped by severity, not by file, since that's
the order a reviewer would actually care about.

## Critical security fixes

| Issue | Where | Fix |
|---|---|---|
| Privilege escalation: any anonymous caller could `POST /api/auth/register?role=ADMIN` and grant themselves admin | `AuthController` | Self-registration now hardcodes `Role.STUDENT`. ADMIN/TEACHER accounts can only be created via the authenticated `/api/admin/users` endpoint. |
| Plaintext password storage: the method backing public self-registration never hashed the password | `SchoolService.createUser()` | Now calls `passwordEncoder.encode()` internally. This was live — not a seed-data-only bug. |
| Path traversal (CWE-22): file download/delete/exists endpoints resolved a client-supplied path with zero sanitization | `FileUploadService` | Every path is now resolved against the upload root and rejected if it escapes it. |
| Missing security beans: `@studentAccessService` / `@teacherAccessService` were referenced in `@PreAuthorize` but never implemented, so students/teachers could not view their own report or upload their own photo | `ReportingController`, `FileUploadController` | Implemented both beans (`security/StudentAccessService.java`, `security/TeacherAccessService.java`). |
| `/api/public/**` was missing from the security allowlist, so the "public" endpoints actually required auth | `SecurityConfig` | Added to `permitAll()`. |
| Wildcard CORS (`@CrossOrigin(origins = "*")`) on 4 of 13 controllers, none on the rest | Multiple controllers | Removed everywhere; replaced by one centralized `config/WebConfig.java` reading allowed origins from `application.properties`. |

## Functional bugs fixed (the app was silently broken in these areas)

| Issue | Where | Fix |
|---|---|---|
| `Attendance.setRemarks()` was a leftover NetBeans stub that unconditionally threw `UnsupportedOperationException` | `entity/Attendance.java` | Implemented as a real, schema-backed field (the DB column already existed; the JPA mapping was just missing). |
| Attendance recording never set `course` on the created record at all — a silent NOT NULL violation on every call | `AttendanceService.recordAttendance()` | Now looks up and sets the course. |
| `GET /api/student/my-status` called `Grade.getScore()` / `Attendance.getStatus()` — two more crash-always stubs | `StudentController` | Rebuilt around a proper `StudentStatusDto`; the stub methods were deleted (they had no backing schema column at all). |
| Same endpoint hardcoded `balance: "0.00"` and `deadline: "None"` regardless of actual fee data | `StudentController` / `SchoolService` | Now computes a real balance (sum of unpaid fees) and real next-due-date from `FeeRepository`. |
| `getAttendancePercentage()` was hardcoded to always return `85.5` | `AttendanceService` | Real calculation: present / total for that student+course. |

## Architecture: entity leaks closed (DTO pattern)

Before this pass, `AdminController`, `StudentController`, `AttendanceController`,
`MessageController`, `TeacherController` all returned raw JPA entities (`User`, `Student`,
`Teacher`, `Grade`, `Attendance`, `Message`) directly, and `AdminController` even accepted
entities as `@RequestBody` for create/update. Fixed across the board:

- New DTOs: `GradeDto`, `AttendanceDto`, `MessageDto`, `UserDto`, `StudentStatusDto`,
  `CreateUserRequest`, `UpdateUserRequest`, `UpdateStudentRequest`, `UpdateTeacherRequest`
- Extended existing-but-underused DTOs: `AdminStudentDto` (+email/phone/cgpa),
  `AdminTeacherDto` (+email/phone/qualification/experience/department)
- `AdminService` rewritten to work in DTOs end-to-end and throw `ResourceNotFoundException`
  (proper 404s) instead of generic `RuntimeException` (which was mapping to 400)
- Every request DTO now has Bean Validation annotations (`@NotBlank`, `@Email`, `@Size`,
  `@Min`, etc.) — previously zero DTOs had any validation at all

## Other cleanup

- Deleted `controller/Exception.java` — a dead, non-functional duplicate of
  `GlobalExceptionHandler`. Its one real behavior (409 on duplicate records via
  `DataIntegrityViolationException`) was merged into the actual handler.
- Deleted `src/main/resources/templates/*.html` — four Thymeleaf-style files with no
  Thymeleaf dependency on the classpath and no controller ever returning their view names.
  Dead weight from an earlier, non-decoupled iteration of the app.
- Removed `SisApplication`'s redundant seed `CommandLineRunner` — duplicated
  `SampleDataLoader`, and (after the password-encoding fix) would have double-encoded the
  admin password and broken that login. `SampleDataLoader` is now the single seeding path.
- `SampleDataLoader` no longer logs demo passwords to the application log; documented in
  `README.md` → **Demo Credentials** instead.
- Removed a stray `bin/` directory (a full Eclipse compiled-output tree, including a stale
  duplicate `pom.xml` pinned to a nonexistent Spring Boot version) that had been
  accidentally included in the zip; added `bin/` to `.gitignore`.
- `application.properties`: database renamed to `sis_db` as requested;
  `spring.jpa.hibernate.ddl-auto=update` replaced with `validate` +
  `spring.sql.init.mode=always` so the existing (renamed) `schema.sql` is the actual
  source of truth for schema instead of Hibernate silently auto-altering tables.
- `SecurityConfig` modernized to the lambda DSL (`authorizeHttpRequests(auth -> ...)`
  instead of the deprecated `.antMatchers().and()` chaining), with `.cors()` wired to
  `WebConfig`, and coarse `/api/admin/**` / `/api/teacher/**` / `/api/student/**` role
  gates added as defense-in-depth alongside the existing per-controller `@PreAuthorize`.
- `pom.xml`: added Lombok (used in the new DTOs).

## Frontend — session 2

The uploaded project's `static/` folder turned out to hold two separate frontends:
`index.html` + `app.js` (reachable, actually served) and `admin-dashboard.js` +
`admin-dashboard.css` + `forms.js` + `forms.css` (715 lines of a considerably more
complete CRUD admin console, but with **no HTML page anywhere that loaded it** — its
only pairing, `templates/admin-dashboard.html`, was deleted last session because it was
never servable in the first place: no Thymeleaf engine configured, no controller ever
returned that view name).

### `index.html` / `app.js` — brought in line with the real backend contract

Every one of these was calling a URL or expecting a field name that didn't match the
actual API (some because I renamed things during the DTO refactor, most because they
never matched even before this refactor started):

| Frontend call | Problem | Fix |
|---|---|---|
| `POST /admin/students` | Real endpoint is `/admin/students/admit` (always was) | URL corrected |
| `POST /admin/courses` | Endpoint didn't exist anywhere in the backend at all | Added `GET`/`POST /api/admin/courses` to `AdminController`, wired to the already-written-but-never-called `SchoolService.createCourse()` |
| Grade submission sent `{studentId, grade}` | `GradeRequest` requires `courseId` (`@NotNull`) and the field is `gradeValue`, not `grade` — this would 400 on every real attempt | Added a course dropdown to the form, fixed field names |
| `GET /teacher/my-courses` | Endpoint didn't exist anywhere | Added it (`TeacherController` + `SchoolService.getCoursesForTeacher()` + `CourseRepository.findByTeacherId()`) |
| `GET /admin/analytics` | Real endpoint is `/admin/statistics` | URL corrected |
| `data.deadline`, `g.score`, `a.status` on the student dashboard | Real `StudentStatusDto`/`GradeDto`/`AttendanceDto` fields are `nextDueDate`, `gradeValue`, and a `present` boolean | Field names corrected; attendance status now derived from `present` |
| "View Public Portal" button | Markup existed in `index.html`; no JS behind it at all | Wired up, calls `/api/public/{announcements,courses,stats}` |

### Resurrecting `admin-dashboard.js` as a real page

Created `static/admin-dashboard.html` with every element ID/modal the existing JS
already expected, and fixed what surfaced once it was actually reachable:

- `admitStudent()` / `hireTeacher()` were sending a nested `user: {...}` object -
  the shape of the *old*, pre-refactor entity-based API. Flattened to match the real
  `CreateStudentRequest` / `CreateTeacherRequest`.
- Used a different `localStorage` key (`authToken`) than `app.js` (`schoolPortalToken`)
  - logging in on the main portal wouldn't have carried a session over. Unified.
  - Added a client-side redirect for non-admin sessions (the real enforcement is
    still server-side `hasAuthority('ADMIN')`; this just avoids dropping a student/
    teacher token onto a page that would otherwise 403 on every request).
- `UserDto` needed an explicit `@JsonProperty("isActive")` - Lombok's `@Data` on a
  `boolean active` field produces a JSON key of `active`, but the frontend (written
  against the old `User.getIsActive()` entity getter) expected `isActive`.
- Caught in my own first draft before shipping it: the "Back to Portal" link shared
  the `.nav-link` class with the tab buttons, and the JS attaches a global click
  handler to every `.nav-link` that calls `preventDefault()` - it would have silently
  eaten the navigation. Moved to its own unstyled link.
- The four "View all →" stat-card links had no click handler anywhere - wired to
  `switchTab()` directly.
- Added CSS for `.badge-admin` / `.badge-teacher` / `.badge-student` - referenced by
  `loadUsers()` but never defined anywhere.
- `service-worker.js`: bumped `CACHE_NAME` (`v1` → `v2`) so browsers with the old
  `app.js` cached actually fetch the new one instead of serving stale cache-first
  assets indefinitely, and added the new/resurrected files to the precache list.
- Final QA pass (verifying every field admin-dashboard.js reads against the actual DTO
  it calls): `AdminStudentDto` was missing `address` even though `Student` has it,
  `UpdateStudentRequest` already accepted it, and the edit-student form already had a
  field for it - the edit modal would always show a blank Address regardless of the
  saved value. Added the field end-to-end (DTO + `AdminService` mapping).

## Frontend — session 3: full rebuild

Replaced the entire frontend with a single, cohesive new one rather than continuing to
patch two divergent, differently-styled UIs (the minimal `app.js` dashboards and the
resurrected `admin-dashboard.js` console from session 2). One codebase now covers
every role.

### Design

A deliberate visual identity instead of the previous generic Bootstrap look:
**ink navy + brass on cool ledger-paper**, with a wax-seal/stamp motif as the signature
element (fitting for a system built on transcripts, admissions, and grades) - ruled
"ledger" tables with hairline row dividers, Fraunces for headers, IBM Plex Sans for UI
text, IBM Plex Mono for data (grades, IDs, dates). All tokens and components live in
the static CSS files under `src/main/resources/static/css/`.

### What's actually there now

- `index.html` — one shell: login, public portal, and the post-login app frame
- `css/style.css`, `css/dashboard-style.css`, and page-specific stylesheets
- `js/auth.js` plus page-specific scripts for the split static entrypoints
  (`VIEW_RENDERERS`, one function per nav destination) over the real API:
  - **Admin**: dashboard stats (with a live chart), full student CRUD (admit, edit,
    remove, CGPA view/recalculate), full teacher CRUD (hire, edit, remove), course
    creation, full user-account management (create, deactivate, restore, view
    deactivated accounts), announcements
  - **Teacher**: my courses, grade entry, attendance entry, student lookup (by ID or
    course roster), my profile, announcements
  - **Student**: dashboard overview (fee balance, recent grades/attendance), grades,
    attendance, courses (with materials per course), fees, messages (inbox/sent/
    compose/mark-read), announcements
  - Every request/response field verified against the actual DTO it corresponds to
    (this is the same verification pass described in the session-2 entries above,
    just carried through to a full rewrite rather than patches)
  - Native HTML5 form validation throughout, styled consistently
  - Every place user-supplied text gets rendered is passed through an `esc()` helper
    (announcement titles/content, names, messages, remarks) - checked explicitly to
    make sure nothing user-controlled reaches `innerHTML` unescaped

### Removed

`css/style.css`, `css/admin-dashboard.css`, `css/forms.css`, `js/admin-dashboard.js`,
`js/forms.js`, `js/performance-monitor.js`, `admin-dashboard.html` - all superseded.
`performance-monitor.js` in particular was never wired into any page in the original
upload either; not worth carrying forward into a fresh build with nothing using it.

### Supporting changes

- `manifest.json` / `service-worker.js` updated for the new file list, palette, and
  name ("Registrar"); cache bumped to `v3`
- New `icons/icon.svg` — a wax-seal mark matching the new palette, replacing the
  generic default icon
- `README.md` / `DEPLOYMENT_GUIDE.md`: rewrote every section describing the frontend
  (Architecture Overview, Frontend Components, Access URLs, Troubleshooting, Support &
  Documentation) to match what's actually there now. Also fixed a documentation bug
  that predates this whole refactor: `DEPLOYMENT_GUIDE.md` pointed at
  `http://localhost:8080/templates/admin-dashboard.html` as the admin URL - that was
  never reachable even in the original codebase (Spring Boot doesn't serve
  `src/main/resources/templates/` directly; that requires a view resolver, which this
  app never had).

## Session 4: IDE readiness + a real startup-blocking bug

Asked to make the project ready to open and run in IntelliJ IDEA with nothing missing.

### Confirmed: full `mvn compile` still isn't possible here
Tried directly this time (installed a JDK and Maven via apt, both of which *are*
network-reachable) - `mvn compile` fails with a 403 from `repo.maven.apache.org`,
which isn't on this sandbox's allowlist. Documented, not worked around.

### Critical fix: the app would have crashed on startup against a real database
Systematically cross-checked every entity field's resolved column name (respecting
`@Column(name=...)` overrides, and Hibernate's default camelCase→snake_case
conversion where there's no override) against every column `schema.sql` actually
creates, across all 13 entities. Found one real mismatch: `Fee.paidDate` had no
explicit `@Column` name, so Hibernate's default naming would look for a `paid_date`
column - but `schema.sql` defines it as `payment_date`. With
`spring.jpa.hibernate.ddl-auto=validate` (set earlier this session, specifically to
fail fast on exactly this kind of drift instead of silently auto-altering the
database), this would have thrown a `SchemaManagementException` and refused to start,
every time, against a real MySQL database - undetected all session because nothing
before now actually attempted schema validation against real column names. Fixed
with `@Column(name = "payment_date")`. Re-ran the same check against all other
entities/fields afterward - clean.

### New: `src/main/resources/sample_data.sql`
Realistic seed data (1 admin, 3 teachers, 6 students, 4 courses, enrollments, grades,
attendance, fees - mixed paid/outstanding, announcements, course materials, messages)
matching the live schema exactly, for anyone who wants to populate a database by hand
rather than rely on `SampleDataLoader`'s programmatic seeding. Passwords are real
BCrypt hashes (generated and verified, not placeholders) for `admin123` / `teacher123`
/ `student123`, matching the same demo credentials documented elsewhere.

### New: `src/test/resources/application.properties` + H2 test dependency
`SisApplicationTests` (`@SpringBootTest`) had no test-specific configuration at all,
so it inherited the real MySQL datasource from the main `application.properties` -
meaning `mvn test` (or IntelliJ's "Run Tests") would fail immediately in any
environment without a MySQL server already running, including a fresh IDE checkout.
Added H2 as a test-scope dependency and a test properties file pointing at an
in-memory instance (`ddl-auto=create-drop`, schema generated from the entity mappings
directly rather than running the MySQL-flavored `schema.sql`). The test suite - and
by extension `SampleDataLoader`'s seeding logic, since it runs as part of context
startup - now exercises real functionality without needing any external database.

### New: `IDE_SETUP.md`
Step-by-step IntelliJ IDEA setup, written around the failure modes that actually bite
people on a Spring Boot + Lombok project: the Lombok plugin + "Enable annotation
processing" checkbox (without which IntelliJ shows red errors on every
Lombok-generated getter/setter even though the code compiles fine from the terminal),
JDK/project SDK verification, running the test suite first (works without MySQL, per
above) before configuring a real database, and the Windows-specific
`file.upload.directory` default that needs changing on Linux/Mac.

## Session 5: a second frontend upload (MIT.zip) - findings, not yet integrated

Uploaded `MIT.zip` and asked to use it as the frontend, with sample SQL and a testing
review. Investigated before touching anything, since the ask implied swapping out the
working "Registrar" frontend:

- The zip bundles two unrelated things: a genuinely nice **frontend-only** MIT-branded
  (Makerere Institute of Technology) UI demo - login, dashboard, and 5 modules
  (academics, attendance, finance, profile, campus-support) - plus a completely
  separate **Node.js/Express/MongoDB** backend scaffold (`server.js`/`routes.js`/
  `models.js`) with matching Jest tests. Confirmed via `grep` that **zero** frontend
  JS files make any `fetch()` call anywhere - every screen runs entirely on
  `localStorage` mock state (hardcoded OTP `123456`, a fake client-side CAPTCHA, a
  hardcoded username→display-name lookup table). One of the test files still contains
  the literal path `mnt/user-data/outputs/tests/` - Claude's own output-directory
  convention - strong evidence this got bundled in from an unrelated session by
  accident rather than being an intentional part of this project.
- The Node/Mongo backend and its Jest tests don't apply to `mit_portal-main` at all
  (different language, different database, different data model) - not integrated.
- The frontend's modules assume real backend features that don't exist here: Finance
  expects invoices/receipts/scholarships, Campus Support expects hostel/transport/
  clubs/events/an AI chat tab. None of that is in the Spring Boot backend's schema or
  API surface. Login, Dashboard, Academics (grades/courses), Attendance, and basic
  Profile map reasonably well; Finance and Campus Support do not, without real backend
  work first.
- Given the scope (~9,900 lines across the frontend alone, deep mock-data patterns
  in every function) and that I can't verify a rewrite of this size the way everything
  else this session was verified (line-by-line reading plus the systematic
  cross-checks above), I did not attempt a full swap in the same pass as finding the
  mismatch - delivered the concretely verifiable pieces (sample SQL, the schema bug
  fix, H2 test setup) and flagged the frontend-swap scope/tradeoffs for a decision
  before investing that effort.

## Session 6: MIT frontend integration begins - login + dashboard, real end-to-end

Copied the MIT-branded frontend (`index.html`, `dashboard.html`, and the 5 module
pages) into `static/`, replacing "Registrar." Wired the two most foundational pieces
completely and verifiably rather than making a shallow pass across all ~9,900 lines:

**Login (`js/script.js`)**: removed the fake CAPTCHA and the OTP step (hardcoded to
accept `123456`) - neither corresponds to anything the backend implements, and
shipping fake security theater next to a real login is worse than not having it.
`POST /api/auth/login` now runs for real; a non-STUDENT account is told to use the
staff portal instead (the role comes back from the server response, never from
anything the client claims - the same principle behind the `/api/auth/register` fix
earlier this session).

**Dashboard (`js/dashboard-script.js`)**: the auth guard was previously a no-op
(`// window.location.href = 'index.html'` was commented out, with the comment
"continuing for demo") - now actually redirects if there's no valid session. The
profile name used a hardcoded lookup table (`stu001 -> "John Doe"`); now pulls from
`GET /api/student/profile`. The four stat cards, course list, "Upcoming Deadlines"
(repurposed to show real recent grades - there's no assignment-deadline concept in
the backend), the payment/fee card, and the Announcements card are all populated from
real endpoints (`/api/student/profile`, `/api/student/my-status`, `/api/announcements`)
instead of static placeholder HTML. Module navigation previously just showed an
`alert()` saying what it "would" do - now actually navigates. Found and fixed a
missing field along the way: `StudentProfileDto` had no `cgpa`, so a student had no
way to see their own CGPA anywhere (only the admin-only endpoint exposed it) - added
it, mirroring the entity field directly.

Two stat cards ("Credits", "Semester") are left showing "—" rather than a fabricated
number - neither is tracked anywhere in the backend (no per-course credit totals or
semester-progression field), and this session's whole approach has been to label
gaps honestly rather than fake them.

**Not yet done**: `js/modules-script.js` (shared by academics/attendance/finance/
profile/campus-support - 876 lines) still checks for the old, now-removed
`studentLogin` localStorage key, so clicking from the dashboard into any of those
five pages will not work correctly yet. `admin-script.js` and `admin-login.html` are
also unchanged - the original login page there lets the caller pick their own role
from four buttons (admin/faculty/finance/registrar) and sends it directly to a
(nonexistent) backend, which is exactly the client-chooses-its-own-privilege pattern
fixed in `AuthController.register` earlier this session - that needs the same fix
before it's wired to anything real.

## Session 7: build verification, then Academics/Attendance/Finance/Profile wired

Uploaded a zip containing `.idea/` and `target/` from actually building the project in
IntelliJ - reviewed it: **95/95 main classes + 1/1 test class compiled with zero
errors**, `diff -rq` against my working copy came back completely empty (built exactly
what was delivered, unmodified), annotation processing was enabled (Lombok ran
correctly). First real compiler validation of everything this session's static
analysis predicted would work. No changes needed from that review.

Continued the planned next phase: wired `js/modules-script.js` (shared by Academics,
Attendance, Finance, and Profile - 1,232 lines after edits) to the real backend.

- **Session/logout**: replaced the old `studentLogin` guard (which the new login flow
  never writes) with a real check against the JWT session, shared across all 5 pages.
- **Academics**: My Courses cards and the Grades/Transcript table now render from real
  `/api/student/courses` + `/api/student/grades` + `/api/student/profile` data (for
  CGPA) instead of static placeholder cards. Fields with no backend equivalent
  (per-course progress %, assignment counts, course codes, credits, semester
  numbers) show `—` rather than a fabricated number, following the same principle as
  every other honestly-labeled gap this session.
- **Attendance**: rebuilt the Overview tab's summary cards and per-course breakdown,
  and the Records table, from real `/api/student/attendance` data. "Late Arrivals"
  shows 0, since the backend's `present` field is a plain boolean with no third state.
- **Finance**: this one mapped more cleanly than expected - `/api/fees` already covers
  the Summary tab's fee table and the Pending Payments tab entirely. Invoices,
  Receipts, and Scholarships tabs still have no backend equivalent and are untouched.
- **Profile**: found a second missing-field gap matching the CGPA one from the
  previous session - `StudentProfileDto` had no `email`, `phone`, or `address` at all,
  even though `Student` (the entity) already has all three. Added them the same way
  CGPA was added, then wired the Personal Info and Academic Info tabs. Date of Birth,
  Gender, Registration Number, Current Year/Semester, Credits Completed, and
  Admission Date are genuinely not tracked anywhere in the backend and are left as
  their original static values.
- **Campus Support**: untouched, as previously flagged - no backing entities exist
  for hostel/transport/clubs/events/chat.

**Still pending**: `admin-login.html`/`admin-script.js` - the admin login currently
lets the caller pick their own role from four buttons and needs the same
"role must come from the server" fix `AuthController.register` got earlier this
session before it's wired to anything real.

## Session 8: enterprise P0-P3 security/architecture review, fully implemented

Given a detailed, prioritized review (P0 critical security, P1 high-impact
correctness/performance, P2 architecture, P3 longer-term hygiene) to implement
systematically. Below is the final state after implementing it - see the note at the
end about a mid-session environment reset that required redoing part of this work.

### P0 - Critical Security (all done)

- **All secrets externalized.** `application.properties` now reads `DB_URL`,
  `DB_USER`, `DB_PASSWORD`, `JWT_SECRET`, `UPLOAD_DIR`, `MAIL_USERNAME`,
  `MAIL_PASSWORD`, etc. via `${VAR:default}`, with the fallback values being local-dev
  conveniences only (a real, randomly-generated JWT secret so dev works out of the
  box, but documented as public/not-for-production since it's committed).
- **Soft-delete authentication bypass fixed.** `CustomUserDetailsService` had
  `.disabled(false)` hardcoded - a deactivated account (`AdminService.removeUser`
  etc., which only ever sets `isActive=false`) could still log in successfully. Now
  `.disabled(!Boolean.TRUE.equals(user.getIsActive()))`.
- **Unprotected file endpoints fixed.** `GET /api/files/download`, `/exists`, `/info`
  had no `@PreAuthorize` at all - any authenticated user could fetch any other
  student's or teacher's profile photo by guessing/enumerating filenames. New
  `FileAccessService` recovers the owning student/teacher ID from the filename
  convention (`{ownerId}_{uuid}.ext}`, the same one `FileUploadService` already uses)
  and checks it via the existing `StudentAccessService`/`TeacherAccessService` beans.
- **JWT secret length validated at startup.** `JwtService.init()` (`@PostConstruct`)
  fails fast with an actionable message if the configured secret is under 32 bytes
  (HS256's minimum), instead of letting `Keys.hmacShaKeyFor` throw a much less
  useful `WeakKeyException` on the first login attempt in production. Also now
  caches the signing `Key` as a field instead of rebuilding it from the secret string
  on every single token issue/validate call.

### P1 - High-Impact Correctness & Performance (all done)

- **JWT moved off `localStorage` entirely**, to `HttpOnly` + `Secure` (env-controlled,
  since local http:// dev can't use Secure cookies) + `SameSite=Strict` cookies, with
  a real, server-side-revocable refresh-token flow:
  - New `refresh_tokens` table + `RefreshToken` entity + `RefreshTokenRepository` +
    `RefreshTokenService`. Refresh tokens are opaque random strings (not JWTs - no
    claims of their own), stored **hashed** (SHA-256), matching how passwords are
    never stored in plaintext either.
  - `AuthController` now sets cookies on login (`access_token` 1h, `refresh_token`
    7d, scoped to `/api/auth`) instead of returning the raw token in the JSON body -
    `AuthResponse` no longer has a `token` field at all, only `role`/`username`.
    Returning the token in the body would have undermined the whole point (an XSS
    bug could just read it from the fetch() response instead of localStorage).
  - New `POST /api/auth/refresh` (exchanges the refresh cookie for a new access
    cookie without re-entering credentials) and `POST /api/auth/logout` (revokes the
    refresh token server-side, clears both cookies).
  - Refresh tokens are also revoked on password reset, admin-initiated password
    change, and account deactivation (`removeUser`/`removeStudent`/`removeTeacher`) -
    an old session shouldn't survive any of those.
  - `JwtAuthenticationFilter` now checks the cookie first, falling back to the
    `Authorization: Bearer` header for non-browser API clients.
  - **Centralized frontend auth logic into `js/auth.js`**, loaded on every page,
    replacing what was duplicated three times across `script.js`/
    `dashboard-script.js`/`modules-script.js`: the fetch wrapper (`apiFetch`/
    `apiGet`, now with `credentials: 'include'` and a silent refresh-and-retry on a
    401 before giving up), the session guard (`requireRole`), and `logout()`.
- **Fixed a real, live XSS vulnerability.** `modules-script.js`'s chat message
  renderer put raw, unescaped user input straight into `innerHTML` - typing
  `<img src=x onerror=alert(1)>` into the chat box would execute immediately.
  Rebuilt with `createElement`/`textContent` instead of string-interpolated
  `innerHTML`. Swept every other frontend JS file for the same pattern - clean.
- **Fetch strategy fixed.** Every default-EAGER `@ManyToOne`/`@OneToOne` across all
  entities (`Course.teacher`, `Attendance.student`/`course`, `Grade.student`/`course`,
  `Fee.student`, `CourseMaterial.course`/`uploadedBy`, `Announcement.author`,
  `Student.user`, `Teacher.user`) now explicitly `fetch = FetchType.LAZY`.
- **N+1 queries fixed with the paired change**: new `@Query(...JOIN FETCH...)`
  repository methods (`StudentRepository.findAllWithUser`,
  `TeacherRepository.findAllWithUser`, `CourseRepository.findAllWithTeacher`/
  `findByTeacherIdWithTeacher`, `GradeRepository.findByStudentIdWithCourse`,
  `AttendanceRepository.findByStudentIdWithCourse`,
  `StudentRepository.findByIdWithCoursesAndTeachers`), wired into `SchoolService`/
  `AdminService` in place of the plain `findAll()`/`findById()` calls that used to
  cause a separate query per row once the fetch types above went LAZY.
- **Pagination added** to the admin students/teachers/courses list endpoints -
  `GET /api/admin/{students,teachers,courses}` now accept `?page=&size=&sort=` and
  return a real Spring Data `Page<T>` (`content`, `totalElements`, `totalPages`,
  etc.) instead of an unbounded `List<T>`.

### P2 - Architecture & Design

- **`SchoolService` split into focused services.** It was one class covering ten
  domains (users, students, teachers, courses, grades, attendance, announcements,
  fees, materials, messages). Extracted the real logic into four new,
  independently-testable services - `AcademicRecordService` (grades/attendance/
  student-status), `FeeService`, `MessagingService` (announcements + messages),
  `CourseMaterialService` - each owning exactly one concern. `SchoolService` itself
  is now a much smaller class (user/student/teacher/course/roster methods) plus a
  thin delegating facade for the extracted methods, so every existing controller
  keeps injecting `SchoolService` with zero constructor changes - a deliberate,
  lower-risk way to get real separation of concerns without touching (and
  re-verifying, without a compiler available) seven controllers' constructor
  injection in the same pass.
- **Cascade fixed.** `Student.user`/`Teacher.user` changed from
  `cascade = CascadeType.ALL` to `{PERSIST, MERGE}` only - `ALL` included `REMOVE`,
  which would have silently hard-deleted the linked `User` row if either entity were
  ever removed. Nothing currently hard-deletes a Student/Teacher at all
  (`AdminService` only ever soft-deletes via `isActive=false`), so this was a safe,
  contained change; if a real hard-delete path gets added later, it now has to
  explicitly decide what happens to the `User` row rather than relying on cascade.
- **Rate limiting added** on `/api/auth/**` - new `RateLimitFilter`, a simple
  in-memory token-bucket keyed by client IP. (Updated later: forwarded addresses
  are now honored only from direct peers listed in
  `ratelimit.auth.trusted-proxies` / `AUTH_TRUSTED_PROXY_IPS`; configure reverse
  proxies to overwrite or append the actual connecting client address.) Documented
  as a single-instance solution; a multi-instance deployment behind a load balancer
  would need a shared store (Redis, etc.) instead.
- **`GlobalExceptionHandler` already mapped `AccessDeniedException` → 403** and
  already kept the generic `Exception` handler's response message
  production-safe (no stack trace or SQL detail) - reviewed, no change needed.
- **SecurityConfig/`@PreAuthorize` sync**: verified throughout this pass rather than
  as a single discrete step - every new/changed endpoint got its authorization
  annotation added in the same commit as the endpoint itself.

### P3 - Longer-term Hygiene

- **`MIGRATION_TO_BOOT3.md`** - a concrete, scoped Spring Boot 3.x/Jakarta migration
  guide (the actual `javax.*` → `jakarta.*` inventory for this codebase - 38 files,
  4 packages; the `pom.xml` changes needed including the MySQL connector coordinate
  rename; the one Spring Security 6 API change relevant here - `antMatchers` →
  `requestMatchers`; what does and doesn't need to change). Deliberately
  documentation, not an attempted migration - too large and high-blast-radius to do
  blind, without a compiler, in the same pass as everything else here.
- **CSP and security headers added** to `SecurityConfig` - a same-origin-only
  Content-Security-Policy (explicitly allow-listing the one real external dependency,
  Chart.js from a CDN), `X-Frame-Options: DENY`. Documented `style-src 'unsafe-inline'`
  as a known, deliberate gap (the pages use inline `style=""` throughout) rather than
  silently dropping the header to avoid the tradeoff.
- **Database indexes**: found and fixed three real gaps - `courses.teacher_id` had no
  index at all despite being the exact column the new `findByTeacherId*` queries
  filter on; `course_students` only had a composite PK covering course-id-first
  lookups, missing an index for the student-id-first direction
  (`student.getCourses()`); `messages` had an index on `receiver_id` but not
  `sender_id`, despite `getSentMessages()` querying that column directly.
- **New `SecurityIntegrationTest`** - a real end-to-end test (not a mocked slice)
  against the actual application context (H2, per the existing test profile),
  exercising the real `SecurityConfig`, the real `JwtAuthenticationFilter`, and the
  real cookie-based login flow: confirms public endpoints are reachable without
  auth, protected endpoints reject unauthenticated requests, login sets a real
  HttpOnly cookie and never leaks the raw token in the response body, invalid
  credentials are rejected, and - directly re-testing the privilege-escalation fix
  from earlier in this project's history - that public self-registration can never
  produce anything but a STUDENT account regardless of what the caller sends.
  Testcontainers-based integration tests (real MySQL instead of H2) are a reasonable
  next step but weren't added here - H2 already gives meaningful coverage without
  requiring Docker in whatever environment runs the test suite.

### A mid-session environment reset

Partway through this pass, the sandbox environment reset and the entire working
directory was lost - everything done in this session up to that point had not yet
been checkpointed (re-zipped and delivered). Recovered by restoring from the last
delivered zip and redoing the lost work; from that point on, checkpointed (re-zip +
deliver) after each major milestone (P0 done, P1 done, P2 done) rather than waiting
until everything was finished, specifically to bound the damage if it happened again.

### Also found and fixed along the way (not from the review, but real bugs)

- **None of the 8 static HTML pages built in earlier sessions were in
  `SecurityConfig`'s `permitAll` list** - Spring Security would have returned 401 for
  `academics.html`, `dashboard.html`, etc. before the page could even load and
  redirect to login. This means the multi-page navigation built in sessions 6-7 was
  never actually reachable against a real, security-enforcing backend - fixed by
  adding all 8 pages to the allowlist (they carry no student data themselves; real
  data only ever arrives via authenticated `/api/**` calls after the page loads).
- Reconciled 3 real entity/schema type mismatches (`Announcement.content`,
  `CourseMaterial.description`, `Message.content` were mapped with `length=N`,
  producing `VARCHAR(N)`, when `schema.sql` actually defines all three as `TEXT`)
  found in a separately-uploaded snapshot of this project from a parallel session -
  merged in the correct fix, rejected one incorrect regression (`User.isActive`
  mapped as `BIT` in that same snapshot, when `schema.sql` says `TINYINT(1)`) after
  checking against `schema.sql` directly.

## Session 9: admin-login.html fixed, full admin console built

Closed out the one item flagged as still-pending at the end of Session 8.

### Fixed the admin-side privilege-escalation vulnerability

`admin-login.html` let the caller pick their own role from four buttons
(Admin/Faculty/Finance/Registrar) client-side, with a hardcoded
`email === 'admin@mit.ac.ug' && password === 'admin123'` check backing the whole
thing - not wired to any real backend at all. Same category of bug as the
`AuthController.register` fix from Session 8. Fixed the same way:

- Removed the role selector entirely, not just hidden it - keeping four buttons that
  don't affect the real login would just be a subtler version of the same deception.
- Login now calls the real `POST /api/auth/login`. The role that matters comes back
  from the server, never from anything the client sent.
- A STUDENT account is bounced back to the student portal. A TEACHER account
  authenticates successfully but is told plainly that this console is admin-only for
  now (there's no reduced teacher-scoped view built into this UI) rather than
  silently getting full admin access, or silently getting a console half its buttons
  don't work in.

### Built the actual admin console

`admin-script.js` was, before this, 100% `alert()` placeholders - `loadAdminData()`
didn't even fetch anything, every button just showed a message describing what it
"would" do. No modal markup existed anywhere in `admin-dashboard.html`. Built for
real, against the actual `/api/admin/*` endpoints from Sessions 1-8:

- **Dashboard**: real student/teacher/course counts. "Revenue" and the fake trend
  deltas (`↑ 45 this month` etc.) show `—`/nothing - neither is tracked anywhere in
  the backend, and fabricating a number would be exactly the kind of fake-stub
  behavior this whole project has been about fixing, not reintroducing.
- **Students**: real paginated list (correctly consuming the `Page<T>` shape from
  Session 8's pagination work: `content`, `totalElements`, `totalPages`), an Admit
  Student modal, an Edit modal, and Deactivate - all built from scratch (new modal
  CSS added to `admin-style.css`, a generic modal container added to
  `admin-dashboard.html`, since none of that existed before).
- **Courses**: real paginated grid, a Create Course modal with a live teacher
  dropdown. Credits and enrolled-student-count from the original static cards are
  left out rather than faked - neither is exposed anywhere in the course-listing API.
- **Staff**: real paginated list, Hire Staff modal, Edit modal, Deactivate.
- Found and fixed a real gap while building this: neither `AdminStudentDto` nor
  `AdminTeacherDto` exposed whether the account was active - so a deactivated
  student/teacher had no way to be shown as such in the admin UI at all. Added an
  `active` field to both, sourced from the linked `User.isActive`.
- Found and fixed a handful of onclick handlers (`toggleNotifications`,
  `toggleProfile`, `viewTask`, `generateFinanceReport`, `saveSettings`) that had no
  backing function defined anywhere - would have thrown `ReferenceError` the moment
  anyone clicked them. Added honest placeholder stubs rather than silently leaving
  them broken.
- **Grades, Finance, Reports, and Settings modules are left as their original
  static/alert-placeholder state** - none has a corresponding backend endpoint (no
  admin-wide grade browsing, no admin-wide fee listing, no report generation, no
  settings persistence/backup/email-test infrastructure). Building those would be
  new backend feature work, not wiring existing functionality - clearly out of scope
  for "wire this admin console to the API that already exists."

This closes out every explicitly-tracked gap in this project as of this session.

## Explicitly out of scope (flagged, not changed)
- **`ReportingController`'s report endpoints return plain `String`** rather than
  structured JSON. Left as-is — re-shaping those into DTOs is a real API-contract change
  for the frontend consuming them, not a bug fix.
- I could not run `mvn compile` in this environment (no Maven Central access), so this
  was reviewed and edited by careful reading, not compiler-verified. Run
  `./mvnw clean compile` locally before deploying.

## 2026-10-01 follow-up: migration-managed schema

- Enabled Flyway and Hibernate validation in development to match production.
- Development baselines existing non-empty schemas at V1 before applying forward
  migrations; production baseline adoption remains explicitly controlled.
- Made V20's student registration unique constraint creation idempotent for legacy
  schemas that already contain the index.
- Updated the MySQL integration test to start from an empty database, run migrations,
  and validate the resulting schema with Hibernate. Docker was unavailable locally,
  so the Testcontainers execution remains a release-checklist item.

## Student dashboard follow-up: summary query and mobile menus

- `/api/student/my-status` now returns only the five newest approved grades and
  aggregate attendance counts instead of transferring every historical attendance
  record and grade to render a dashboard summary. Full academic-history endpoints
  remain unchanged.
- Student dashboard attendance metrics consume these counts, and narrow-screen
  dropdowns are constrained to the header viewport instead of using a full-viewport
  menu width.
