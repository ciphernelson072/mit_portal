# Manual Test Data Entry Worksheet

Use this worksheet to enter **synthetic test records manually** through the
administrator portal. It is a reference only: it does not create accounts,
import data, or change the database.

> **Test environment only:** use this worksheet only in the approved pilot or
> another test database. Do not enter real student, staff, financial, or
> identity data into a personal test deployment.

## Before entering records

- Sign in using the administrator account you already control.
- Confirm the portal is connected to the intended test database.
- Use the sample values below or replace them with clearly synthetic values.
- Do not use these examples as real people or institutional information.
- Create unique passwords directly in the portal. Do not write passwords in
  this document, screenshots, chat, or source control. Prefer a strong,
  unique temporary password and require a change at first sign-in if the
  workflow offers that option.
- If a required selector is empty, create its parent record first, then return
  to the form.
- Fields marked **Required** are based on application request validation or
  required relationships. The form may impose additional validation. Fields
  marked **Recommended** help exercise the portal but may be optional.

## Recommended entry order

1. Campus
2. Faculty linked to that campus
3. Department linked to that faculty
4. Course category
5. Academic year and term, if using course sections or registration
6. Teacher account and teacher profile
7. Course assigned to that teacher
8. Student accounts and student profiles
9. Enroll students in courses or course sections
10. Add attendance, grades, fees, announcements, and operations records as
    needed for testing

Do not create duplicate campus, department, category, or course codes if those
already exist. Choose the existing record in the portal instead.

## 1. Campus, faculty, department, and course category

Create these foundation records before teachers or courses.

### Campus

| Field | Requirement | Example |
|---|---|---|
| Code | Required; unique; up to 30 characters | `TST-MAIN` |
| Name | Required; up to 150 characters | `Synthetic Main Campus` |
| Address | Optional | `Test address only - Kampala` |

### Faculty

| Field | Requirement | Example |
|---|---|---|
| Code | Required; unique; up to 30 characters | `TST-FAC` |
| Name | Required; up to 150 characters | `Synthetic Faculty of Technology` |
| Campus | Required; select the campus created above | `TST-MAIN` |

### Department

| Field | Requirement | Example |
|---|---|---|
| Code | Required; unique; up to 30 characters | `TST-COMP` |
| Name | Required; up to 150 characters | `Synthetic Computing Department` |
| Faculty | Required; select the faculty created above | `TST-FAC` |

### Course category

| Field | Requirement | Example |
|---|---|---|
| Code | Required; unique; up to 30 characters | `TST-SCI` |
| Name | Required; up to 100 characters | `Synthetic Sciences` |

## 2. Academic year and term (when needed)

Use the academic setup area before creating course sections, timetable entries,
or testing registration windows. Dates below are illustrative; use dates that
make sense for your test.

### Academic year

| Field | Requirement | Example |
|---|---|---|
| Code | Required; up to 20 characters | `TEST-2026` |
| Name | Required; up to 100 characters | `Synthetic Academic Year 2026` |
| Start date | Required | `2026-08-01` |
| End date | Required; after the start date | `2027-07-31` |
| Activate | Required choice | `Yes` only if this should be the active test year |

### Academic term

| Field | Requirement | Example |
|---|---|---|
| Code | Required; up to 30 characters | `TEST-2026-T1` |
| Name | Required; up to 100 characters | `Synthetic Term One` |
| Academic year | Required; select the year above | `TEST-2026` |
| Start date | Required | `2026-08-01` |
| End date | Required; after the start date | `2026-12-15` |
| Registration open | Required choice | `Yes` only while intentionally testing registration |

## 3. Teacher account and profile

Create through the administrator's teacher-management workflow. The teacher
must have a linked login account and a valid home department before assigning
courses.

### Sample teacher A

| Field | Requirement | Example |
|---|---|---|
| Username | Required; unique; 3-50 characters | `pilot.teacher.one` |
| Password | Required; set privately in the portal | Create a unique temporary password; do not record it here |
| Full name | Required | `Amina Example` |
| Email | Optional but recommended; must be valid and unique if supplied | `teacher.one@example.test` |
| Phone | Optional; use a synthetic number | `+256700000101` |
| Home department | Required | `TST-COMP - Synthetic Computing Department` |
| Department/specialization | Recommended | `Computing` |
| Qualification | Recommended | `MSc Computer Science` |
| Years of experience | Optional; zero or greater | `6` |

### Sample teacher B (optional)

| Field | Requirement | Example |
|---|---|---|
| Username | Required; unique | `pilot.teacher.two` |
| Password | Required; set privately in the portal | Create another unique temporary password |
| Full name | Required | `Daniel Sample` |
| Email | Optional but recommended | `teacher.two@example.test` |
| Phone | Optional; use a synthetic number | `+256700000102` |
| Home department | Required | `TST-COMP` |
| Department/specialization | Recommended | `Information Systems` |
| Qualification | Recommended | `MSc Information Systems` |
| Years of experience | Optional; zero or greater | `4` |

## 4. Courses

Create courses after the teacher and category exist. The code should be unique.

### Sample course A

| Field | Requirement | Example |
|---|---|---|
| Course name | Required | `Computing Foundations` |
| Course code | Required; unique; up to 30 characters | `TST-CS101` |
| Course category | Required | `TST-SCI - Synthetic Sciences` |
| Assigned teacher | Required | `Amina Example` |
| Semester | Optional but recommended | `Synthetic Term One 2026` |
| Credits | Optional in request; recommended positive integer | `3` |
| Students | Optional at course creation; enroll through the portal workflow | Add students after creating their profiles |

### Sample course B (optional)

| Field | Requirement | Example |
|---|---|---|
| Course name | Required | `Information Systems Practice` |
| Course code | Required; unique | `TST-IS102` |
| Course category | Required | `TST-SCI` |
| Assigned teacher | Required | `Daniel Sample` |
| Semester | Optional but recommended | `Synthetic Term One 2026` |
| Credits | Optional; recommended positive integer | `3` |

### Optional section and timetable

Only create these if you are testing registration or timetable features.

| Record | Required fields | Example |
|---|---|---|
| Course section | Course, academic term, section code, capacity (at least 1) | `TST-CS101`, `TEST-2026-T1`, `A`, `30` |
| Timetable | Course, academic term, weekday, start time, end time, venue | `TST-CS101`, `TEST-2026-T1`, `MONDAY`, `09:00`, `10:30`, `Test Room 1` |

## 5. Student accounts and profiles

Create through the administrator's student-management workflow. The enrollment
path may generate a registration number; if so, leave it to the system rather
than inventing one.

### Sample student A

| Field | Requirement | Example |
|---|---|---|
| Username | Required; unique; 3-50 characters | `pilot.student.one` |
| Password | Required; set privately in the portal | Create a unique temporary password |
| Full name | Required | `Jordan Example` |
| Email | Optional but recommended; valid and unique if supplied | `student.one@example.test` |
| Phone | Optional; use a synthetic number | `+256700000201` |
| Class/program description | Optional in account creation; recommended | `Year 1 - Computing` |
| Course enrollment | Optional during creation; use the course/registration workflow | `TST-CS101`, `TST-IS102` |
| Registration number | Follow the portal workflow | Let the system generate it if supported |
| Address | Optional; synthetic only | `Synthetic test address 1, Kampala` |
| Date of birth | Optional; synthetic only | `2005-04-12` |
| Gender | Optional; use the user's preferred test value | `Prefer not to say` |
| City / country | Optional; recommended for profile testing | `Kampala / Uganda` |

### Sample student B (optional)

| Field | Requirement | Example |
|---|---|---|
| Username | Required; unique | `pilot.student.two` |
| Password | Required; set privately in the portal | Create a different unique temporary password |
| Full name | Required | `Taylor Sample` |
| Email | Optional but recommended | `student.two@example.test` |
| Phone | Optional; synthetic | `+256700000202` |
| Class/program description | Optional in account creation; recommended | `Year 1 - Computing` |
| Course enrollment | Optional; use course registration workflow | `TST-CS101` |
| Registration number | Follow the portal workflow | Let the system generate it if supported |
| Address | Optional; synthetic only | `Synthetic test address 2, Wakiso` |

After creating each student, verify that the account is active and that it can
only see its own records.

## 6. Optional academic and student-service records

Use these examples only when exercising the corresponding portal modules.

### Enrollment

Select an existing student and course (or section/term when registration uses
sections). Recommended test combinations:

- `pilot.student.one` -> `TST-CS101`
- `pilot.student.one` -> `TST-IS102`
- `pilot.student.two` -> `TST-CS101`

### Attendance

| Field | Requirement | Example |
|---|---|---|
| Student | Required; choose an enrolled student | `pilot.student.one` |
| Course | Required; choose the student's course | `TST-CS101` |
| Date | Required | `2026-09-07` |
| Present | Required choice | `Yes` |
| Late / class times | Optional if shown | `No`, `09:00-10:30` |
| Remarks | Optional | `Synthetic attendance test` |

Do not create duplicate attendance for the same student, course, and date.

### Grade

| Field | Requirement | Example |
|---|---|---|
| Student | Required; must belong to the course | `pilot.student.one` |
| Course | Required | `TST-CS101` |
| Grade value | Required | `B+` |
| Remarks | Optional | `Synthetic grading test` |

### Fee

Only use non-production amounts and clearly label these as test fees.

| Field | Requirement | Example |
|---|---|---|
| Student | Required | `pilot.student.one` |
| Description | Required | `Synthetic pilot tuition` |
| Amount | Required; greater than zero | `100000` |
| Due date | Required | `2026-10-30` |
| Paid status | Set through the supported payment/reconciliation workflow | Leave unpaid unless testing reconciliation |

Do not represent an actual payment as completed. Do not connect real payment
providers or use real financial records for this test.

### Service request

| Field | Requirement | Example |
|---|---|---|
| Request type | Required | `ACADEMIC` |
| Subject | Required | `Synthetic transcript request` |
| Details | Optional but recommended | `Test-only request for exercising review.` |
| Requester | Required by the applicable UI/workflow | `pilot.student.one` |

### Announcement

| Field | Requirement | Example |
|---|---|---|
| Title | Required; up to 200 characters | `Synthetic pilot maintenance notice` |
| Content | Required; up to 5,000 characters | `Test-only announcement. No real institutional notice.` |

## 7. Optional campus operations records

These are administrative records, not a separate `OPERATIONS` login role. The
current application role values are `ADMIN`, `TEACHER`, and `STUDENT`.

### Staff record

Create/link the required user account first, then add the staff profile.

| Field | Requirement | Example |
|---|---|---|
| User account | Required; select an existing account | `pilot.operations.one` |
| Employee number | Required; unique | `TEST-OPS-001` |
| Full name | Required | `Morgan Operations` |
| Title | Required | `Synthetic Operations Coordinator` |
| Department | Optional in the request; recommended | `TST-COMP` |
| Hire date | Required | `2026-01-15` |

### Supplier record

| Field | Requirement | Example |
|---|---|---|
| Supplier code | Required; unique | `TST-SUP-001` |
| Name | Required | `Synthetic Office Supplies` |
| Email / phone | Optional; use `.test` email and synthetic phone only | `supplier@example.test` / `+256700000301` |

### Asset record

| Field | Requirement | Example |
|---|---|---|
| Asset tag | Required; unique | `TST-ASSET-001` |
| Name | Required | `Test Laptop` |
| Description | Optional | `Synthetic inventory record; not a real institute asset.` |
| Purchase value/date | Optional; test-only values | `1200000` / `2026-01-20` |
| Assigned user | Optional; select a test account if applicable | `pilot.operations.one` |

## 8. Entry checklist

- [ ] I am signed in as the authorized administrator.
- [ ] I confirmed this is the intended test database.
- [ ] I created or selected the campus, faculty, department, and category first.
- [ ] Each username, email, course code, department code, and employee number
      is unique.
- [ ] Teacher profiles have a home department and course assignments point to
      existing teachers.
- [ ] Student accounts are linked to the correct courses/sections.
- [ ] I used synthetic data only and did not record passwords in this document.
- [ ] I did not mark a fee paid unless I used the application's test-only
      reconciliation flow.
- [ ] I verified each account's role and access boundaries after entry.
