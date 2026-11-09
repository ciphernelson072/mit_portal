package com.sis.service;

import com.sis.entity.AcademicTerm;
import com.sis.entity.AcademicYear;
import com.sis.entity.Campus;
import com.sis.entity.Course;
import com.sis.entity.CourseCategory;
import com.sis.entity.CourseRegistration;
import com.sis.entity.CourseSection;
import com.sis.entity.CurriculumCourse;
import com.sis.entity.Department;
import com.sis.entity.Faculty;
import com.sis.entity.Program;
import com.sis.entity.RegistrationStatus;
import com.sis.entity.Role;
import com.sis.entity.Student;
import com.sis.entity.StudentLifecycleStatus;
import com.sis.entity.StudentProgram;
import com.sis.entity.Teacher;
import com.sis.entity.TimetableEntry;
import com.sis.entity.User;
import com.sis.dto.CreateCourseSectionRequest;
import com.sis.repository.AcademicTermRepository;
import com.sis.repository.AcademicYearRepository;
import com.sis.repository.CampusRepository;
import com.sis.repository.CourseCategoryRepository;
import com.sis.repository.CourseRegistrationRepository;
import com.sis.repository.CourseRepository;
import com.sis.repository.CourseSectionRepository;
import com.sis.repository.CurriculumCourseRepository;
import com.sis.repository.DepartmentRepository;
import com.sis.repository.FacultyRepository;
import com.sis.repository.ProgramRepository;
import com.sis.repository.StudentProgramRepository;
import com.sis.repository.StudentRepository;
import com.sis.repository.TeacherRepository;
import com.sis.repository.TimetableEntryRepository;
import com.sis.security.StudentAccessService;
import com.sis.security.TeacherAccessService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@DataJpaTest(properties = "sis.sample-data.enabled=false")
@Import({RegistrationService.class, StudentAccessService.class, TeacherAccessService.class})
class RegistrationServiceConcurrencyTest {

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private StudentAccessService studentAccessService;

    @Autowired
    private TeacherAccessService teacherAccessService;

    @Autowired
    private CourseRegistrationRepository registrations;

    @Autowired
    private CourseRepository courses;

    @Autowired
    private CourseCategoryRepository categories;

    @Autowired
    private TeacherRepository teachers;

    @Autowired
    private AcademicYearRepository academicYears;

    @Autowired
    private AcademicTermRepository terms;

    @Autowired
    private CampusRepository campuses;

    @Autowired
    private FacultyRepository faculties;

    @Autowired
    private DepartmentRepository departments;

    @Autowired
    private ProgramRepository programs;

    @Autowired
    private StudentRepository students;

    @Autowired
    private StudentProgramRepository studentPrograms;

    @Autowired
    private CourseSectionRepository sections;

    @Autowired
    private CurriculumCourseRepository curriculumCourses;

    @Autowired
    private TimetableEntryRepository timetableEntries;

    @MockitoBean
    private FinancialHoldService financialHolds;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentRegistrationsCannotExceedSectionCapacity() throws Exception {
        AcademicYear year = new AcademicYear();
        year.setCode("RACE-AY");
        year.setName("Registration Race Year");
        year.setStartDate(LocalDate.of(2026, 1, 1));
        year.setEndDate(LocalDate.of(2026, 12, 31));
        year = academicYears.saveAndFlush(year);

        AcademicTerm term = new AcademicTerm();
        term.setCode("RACE-T1");
        term.setName("Registration Race Term");
        term.setAcademicYear(year);
        term.setStartDate(LocalDate.of(2026, 1, 1));
        term.setEndDate(LocalDate.of(2026, 6, 30));
        term.setRegistrationOpen(true);
        term = terms.saveAndFlush(term);

        Campus campus = new Campus();
        campus.setCode("RACE-C");
        campus.setName("Registration Race Campus");
        campus = campuses.saveAndFlush(campus);
        Faculty faculty = new Faculty();
        faculty.setCode("RACE-F");
        faculty.setName("Registration Race Faculty");
        faculty.setCampus(campus);
        faculty = faculties.saveAndFlush(faculty);
        Department department = new Department();
        department.setCode("RACE-D");
        department.setName("Registration Race Department");
        department.setFaculty(faculty);
        department = departments.saveAndFlush(department);
        Program program = new Program();
        program.setCode("RACE-P");
        program.setName("Registration Race Program");
        program.setAwardTitle("Race Award");
        program.setDepartment(department);
        program.setDurationYears(3);
        program = programs.saveAndFlush(program);

        Teacher teacher = teachers.saveAndFlush(new Teacher(
                new User("registration-race-teacher", "encoded", Role.TEACHER), "Registration Race Teacher"));
        CourseCategory category = categories.saveAndFlush(new CourseCategory("RACE", "Registration Race Category"));
        Course course = new Course("Registration Race Course", teacher);
        course.setCourseCode("RACE101");
        course.setCategory(category);
        course = courses.saveAndFlush(course);

        CurriculumCourse curriculumCourse = new CurriculumCourse();
        curriculumCourse.setProgram(program);
        curriculumCourse.setCourse(course);
        curriculumCourse.setSemesterNumber(1);
        curriculumCourses.saveAndFlush(curriculumCourse);

        CourseSection section = new CourseSection();
        section.setCourse(course);
        section.setAcademicTerm(term);
        section.setSectionCode("RACE-A");
        section.setCapacity(1);
        section = sections.saveAndFlush(section);
        Long sectionId = section.getId();

        Student firstStudent = createStudent("registration-race-first", "First Student", program, year);
        Student secondStudent = createStudent("registration-race-second", "Second Student", program, year);

        when(financialHolds.hasActiveHold(firstStudent.getId())).thenReturn(false);
        when(financialHolds.hasActiveHold(secondStudent.getId())).thenReturn(false);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executor.submit(() ->
                    registerWhenReleased(ready, start, "registration-race-first", sectionId));
            Future<Boolean> second = executor.submit(() ->
                    registerWhenReleased(ready, start, "registration-race-second", sectionId));

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            int successfulRegistrations = (first.get(20, TimeUnit.SECONDS) ? 1 : 0)
                    + (second.get(20, TimeUnit.SECONDS) ? 1 : 0);

            assertThat(successfulRegistrations).isEqualTo(1);
            assertThat(registrations.countBySectionIdAndStatus(sectionId, RegistrationStatus.REGISTERED)).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void studentCanOnlyRegisterForCoursesInAnActiveProgramCurriculum() {
        AcademicYear year = new AcademicYear();
        year.setCode("CURR-AY");
        year.setName("Curriculum Year");
        year.setStartDate(LocalDate.of(2026, 1, 1));
        year.setEndDate(LocalDate.of(2026, 12, 31));
        year = academicYears.saveAndFlush(year);

        AcademicTerm term = new AcademicTerm();
        term.setCode("CURR-T1");
        term.setName("Curriculum Term");
        term.setAcademicYear(year);
        term.setStartDate(LocalDate.of(2026, 1, 1));
        term.setEndDate(LocalDate.of(2026, 6, 30));
        term.setRegistrationOpen(true);
        term = terms.saveAndFlush(term);

        Campus campus = new Campus();
        campus.setCode("CURR-C");
        campus.setName("Curriculum Campus");
        campus = campuses.saveAndFlush(campus);
        Faculty faculty = new Faculty();
        faculty.setCode("CURR-F");
        faculty.setName("Curriculum Faculty");
        faculty.setCampus(campus);
        faculty = faculties.saveAndFlush(faculty);
        Department department = new Department();
        department.setCode("CURR-D");
        department.setName("Curriculum Department");
        department.setFaculty(faculty);
        department = departments.saveAndFlush(department);
        Program program = new Program();
        program.setCode("CURR-P");
        program.setName("Curriculum Program");
        program.setAwardTitle("Curriculum Award");
        program.setDepartment(department);
        program.setDurationYears(3);
        program = programs.saveAndFlush(program);

        Teacher teacher = teachers.saveAndFlush(new Teacher(
                new User("curriculum-registration-teacher", "encoded", Role.TEACHER),
                "Curriculum Registration Teacher"));
        CourseCategory category = categories.saveAndFlush(
                new CourseCategory("CURR", "Curriculum Registration Category"));
        Course course = new Course("Curriculum Registration Course", teacher);
        course.setCourseCode("CURR101");
        course.setCategory(category);
        course = courses.saveAndFlush(course);

        CreateCourseSectionRequest sectionRequest = new CreateCourseSectionRequest();
        sectionRequest.setCourseId(course.getId());
        sectionRequest.setAcademicTermId(term.getId());
        sectionRequest.setSectionCode(" CURR-A ");
        sectionRequest.setCapacity(10);
        CourseSection section = registrationService.createSection(sectionRequest);
        assertThat(section.getSectionCode()).isEqualTo("CURR-A");

        Student student = createStudent(
                "curriculum-registration-student", "Curriculum Registration Student", program, year);
        Long termId = term.getId();
        Long sectionId = section.getId();

        assertThat(registrationService.studentSections(
                "curriculum-registration-student", termId)).isEmpty();
        assertThatThrownBy(() -> registrationService.register(
                "curriculum-registration-student", sectionId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not part of the student's active program curriculum");

        CurriculumCourse curriculumCourse = new CurriculumCourse();
        curriculumCourse.setProgram(program);
        curriculumCourse.setCourse(course);
        curriculumCourse.setSemesterNumber(1);
        curriculumCourses.saveAndFlush(curriculumCourse);

        assertThat(registrationService.studentSections(
                "curriculum-registration-student", termId)).hasSize(1);
        when(financialHolds.hasActiveHold(student.getId())).thenReturn(false);
        assertThat(registrationService.register("curriculum-registration-student", sectionId)
                .getStudent().getId())
                .isEqualTo(student.getId());
        assertThat(registrationService.registeredCounts(java.util.Collections.singletonList(section)).get(sectionId))
                .isEqualTo(1L);
        assertThat(registrations.findCurrentAcademicYearTimetableRegistrations(
                student.getId(), RegistrationStatus.REGISTERED, LocalDate.of(2026, 6, 1)))
                .extracting(item -> item.getSection().getCourse().getId())
                .containsExactly(course.getId());
        assertThat(registrations.findCurrentAcademicYearTimetableRegistrations(
                student.getId(), RegistrationStatus.REGISTERED, LocalDate.of(2026, 7, 1)))
                .isEmpty();

        term.setRegistrationOpen(false);
        terms.saveAndFlush(term);
        assertThat(registrationService.studentSections("curriculum-registration-student", termId)).isEmpty();
        term.setRegistrationOpen(true);
        terms.saveAndFlush(term);

        AcademicTerm previousTerm = new AcademicTerm();
        previousTerm.setCode("CURR-T2");
        previousTerm.setName("Curriculum Later Term");
        previousTerm.setAcademicYear(year);
        previousTerm.setStartDate(LocalDate.of(2026, 7, 1));
        previousTerm.setEndDate(LocalDate.of(2026, 12, 31));
        previousTerm = terms.saveAndFlush(previousTerm);

        TimetableEntry existingSchedule = new TimetableEntry();
        existingSchedule.setCourseId(course.getId());
        existingSchedule.setAcademicTermId(previousTerm.getId());
        existingSchedule.setDayOfWeek("Monday");
        existingSchedule.setStartsAt(LocalTime.of(9, 0));
        existingSchedule.setEndsAt(LocalTime.of(10, 0));
        existingSchedule.setVenue("Room 1");
        timetableEntries.save(existingSchedule);

        Course conflictingCourse = new Course("Conflicting Course", teacher);
        conflictingCourse.setCourseCode("CURR102");
        conflictingCourse.setCategory(category);
        conflictingCourse = courses.saveAndFlush(conflictingCourse);
        CurriculumCourse conflictingCurriculumCourse = new CurriculumCourse();
        conflictingCurriculumCourse.setProgram(program);
        conflictingCurriculumCourse.setCourse(conflictingCourse);
        conflictingCurriculumCourse.setSemesterNumber(1);
        curriculumCourses.saveAndFlush(conflictingCurriculumCourse);

        CourseSection conflictingSection = new CourseSection();
        conflictingSection.setCourse(conflictingCourse);
        conflictingSection.setAcademicTerm(term);
        conflictingSection.setSectionCode("CURR-A");
        conflictingSection.setCapacity(10);
        conflictingSection = sections.saveAndFlush(conflictingSection);

        TimetableEntry conflictingSchedule = new TimetableEntry();
        conflictingSchedule.setCourseId(conflictingCourse.getId());
        conflictingSchedule.setAcademicTermId(term.getId());
        conflictingSchedule.setDayOfWeek("MONDAY");
        conflictingSchedule.setStartsAt(LocalTime.of(9, 30));
        conflictingSchedule.setEndsAt(LocalTime.of(10, 30));
        conflictingSchedule.setVenue("Room 2");
        timetableEntries.save(conflictingSchedule);

        Long conflictingSectionId = conflictingSection.getId();
        CourseRegistration otherTermOnlySchedule = registrationService.register(
                "curriculum-registration-student", conflictingSectionId);
        registrationService.drop("curriculum-registration-student", otherTermOnlySchedule.getId());

        existingSchedule.setAcademicTermId(term.getId());
        timetableEntries.saveAndFlush(existingSchedule);
        assertThat(timetableEntries.findCurrentRegisteredEntries(
                student.getId(), RegistrationStatus.REGISTERED, LocalDate.of(2026, 6, 1)))
                .extracting(TimetableEntry::getCourseId)
                .containsExactly(course.getId());
        assertThatThrownBy(() -> registrationService.register(
                "curriculum-registration-student", conflictingSectionId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("timetable conflicts");

        try {
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken("curriculum-registration-teacher", "n/a",
                            java.util.Collections.singleton(new SimpleGrantedAuthority("TEACHER"))));
            assertThat(teacherAccessService.teachesStudent(student.getId())).isTrue();

            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken("curriculum-registration-student", "n/a",
                            java.util.Collections.singleton(new SimpleGrantedAuthority("STUDENT"))));
            assertThat(studentAccessService.isEnrolledInCourse(course.getId())).isTrue();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void sectionCodeCannotExceedDatabaseColumnLength() {
        CreateCourseSectionRequest request = new CreateCourseSectionRequest();
        request.setCourseId(1L);
        request.setAcademicTermId(1L);
        request.setSectionCode("A".repeat(31));
        request.setCapacity(10);

        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(request))
                    .anySatisfy(violation -> assertThat(violation.getPropertyPath().toString())
                            .isEqualTo("sectionCode"));
        }
    }

    @Test
    void studentCannotDropCourseAfterRegistrationWindowCloses() {
        CourseRegistration registration = createDropRegistration("drop-closed-student", false);

        assertThatThrownBy(() -> registrationService.drop("drop-closed-student", registration.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("dropping is closed");

        CourseRegistration unchanged = registrations.findById(registration.getId()).orElseThrow();
        assertThat(unchanged.getStatus()).isEqualTo(RegistrationStatus.REGISTERED);
        assertThat(unchanged.getDroppedAt()).isNull();
    }

    @Test
    void studentCanDropCourseWhileRegistrationWindowIsOpen() {
        CourseRegistration registration = createDropRegistration("drop-open-student", true);

        registrationService.drop("drop-open-student", registration.getId());

        CourseRegistration dropped = registrations.findById(registration.getId()).orElseThrow();
        assertThat(dropped.getStatus()).isEqualTo(RegistrationStatus.DROPPED);
        assertThat(dropped.getDroppedAt()).isNotNull();
    }

    private CourseRegistration createDropRegistration(String username, boolean registrationOpen) {
        String code = "DROP-" + java.util.UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        AcademicYear year = new AcademicYear();
        year.setCode(code + "-Y");
        year.setName(username + " Year");
        year.setStartDate(LocalDate.of(2026, 1, 1));
        year.setEndDate(LocalDate.of(2026, 12, 31));
        year = academicYears.saveAndFlush(year);

        AcademicTerm term = new AcademicTerm();
        term.setCode(code + "-T1");
        term.setName(username + " Term");
        term.setAcademicYear(year);
        term.setStartDate(LocalDate.of(2026, 1, 1));
        term.setEndDate(LocalDate.of(2026, 6, 30));
        term.setRegistrationOpen(registrationOpen);
        term = terms.saveAndFlush(term);

        Teacher teacher = teachers.saveAndFlush(new Teacher(
                new User(username + "-teacher", "encoded", Role.TEACHER), username + " Teacher"));
        CourseCategory category = categories.saveAndFlush(new CourseCategory(code, username + " Category"));
        Course course = new Course(username + " Course", teacher);
        course.setCourseCode(code + "-101");
        course.setCategory(category);
        course = courses.saveAndFlush(course);

        CourseSection section = new CourseSection();
        section.setCourse(course);
        section.setAcademicTerm(term);
        section.setSectionCode("A");
        section.setCapacity(10);
        section = sections.saveAndFlush(section);

        Student student = students.saveAndFlush(new Student(
                new User(username, "encoded", Role.STUDENT), username + " Student", "Year 1"));
        CourseRegistration registration = new CourseRegistration();
        registration.setStudent(student);
        registration.setSection(section);
        registration.setStatus(RegistrationStatus.REGISTERED);
        return registrations.saveAndFlush(registration);
    }

    private Student createStudent(String username, String fullName, Program program, AcademicYear year) {
        Student student = students.saveAndFlush(new Student(
                new User(username, "encoded", Role.STUDENT), fullName, "Year 1"));
        StudentProgram enrollment = new StudentProgram();
        enrollment.setStudent(student);
        enrollment.setProgram(program);
        enrollment.setAcademicYear(year);
        enrollment.setStatus(StudentLifecycleStatus.ACTIVE);
        studentPrograms.saveAndFlush(enrollment);
        return student;
    }

    private boolean registerWhenReleased(CountDownLatch ready, CountDownLatch start,
                                         String username, Long sectionId) throws Exception {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Registration concurrency test did not start");
        }
        try {
            registrationService.register(username, sectionId);
            return true;
        } catch (IllegalStateException expectedRegistrationConflict) {
            return false;
        }
    }
}
