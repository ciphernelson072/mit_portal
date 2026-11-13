package com.sis.repository;

import com.sis.entity.Course;
import com.sis.entity.CourseCategory;
import com.sis.entity.CourseRegistration;
import com.sis.entity.CourseSection;
import com.sis.entity.AcademicTerm;
import com.sis.entity.AcademicYear;
import com.sis.entity.Exam;
import com.sis.entity.RegistrationStatus;
import com.sis.entity.Role;
import com.sis.entity.Student;
import com.sis.entity.Teacher;
import com.sis.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "sis.sample-data.enabled=false"
})
class CourseEnrollmentRepositoryTest {

    @Autowired
    private CourseRepository courses;

    @Autowired
    private StudentRepository students;

    @Autowired
    private TeacherRepository teachers;

    @Autowired
    private CourseCategoryRepository categories;

    @Autowired
    private AcademicYearRepository academicYears;

    @Autowired
    private AcademicTermRepository terms;

    @Autowired
    private CourseSectionRepository sections;

    @Autowired
    private CourseRegistrationRepository registrations;

    @Autowired
    private ExamRepository exams;

    @Test
    void enrollmentExistenceQueryChecksCourseStudentPair() {
        Student student = new Student(new User("course-member", "encoded", Role.STUDENT),
                "Course Member", "Year 1");
        student.setRegistrationNumber("MIT/2026/ENR01");
        student = students.saveAndFlush(student);

        Teacher teacher = teachers.saveAndFlush(
                new Teacher(new User("course-teacher", "encoded", Role.TEACHER), "Course Teacher"));
        CourseCategory category = categories.saveAndFlush(new CourseCategory("ENR", "Enrollment"));
        Course course = new Course("Enrollment Course", teacher);
        course.setCourseCode("ENR101");
        course.setCategory(category);
        course.setStudents(Set.of(student));
        course = courses.saveAndFlush(course);

        assertThat(courses.existsEnrolledStudent(course.getId(), student.getId())).isTrue();
        assertThat(courses.existsEnrolledStudent(course.getId(), student.getId() + 1)).isFalse();
        assertThat(courses.existsEnrolledStudent(course.getId() + 1, student.getId())).isFalse();
    }

    @Test
    void sectionRegistrationCountsAsCourseEnrollmentForAcademicEligibility() {
        Student student = newStudent("registered-course-member", "MIT/2026/ENR02");
        Teacher teacher = newTeacher();
        CourseCategory category = categories.saveAndFlush(new CourseCategory("REN", "Registered Enrollment"));
        Course course = saveCourse("Registered Enrollment Course", "REN101", teacher, category);
        CourseSection section = saveSection(course, saveTerm("REN-AY", "REN-T1"));

        CourseRegistration registration = new CourseRegistration();
        registration.setStudent(student);
        registration.setSection(section);
        registration.setStatus(RegistrationStatus.REGISTERED);
        registrations.saveAndFlush(registration);

        assertThat(courses.existsEnrolledStudent(course.getId(), student.getId())).isTrue();

        registration.setStatus(RegistrationStatus.DROPPED);
        registrations.saveAndFlush(registration);
        assertThat(courses.existsEnrolledStudent(course.getId(), student.getId())).isFalse();
    }

    @Test
    void studentExamLookupIncludesLegacyAndRegisteredCoursesButNotDroppedCourses() {
        Student student = newStudent("exam-course-member", "MIT/2026/EXM01");
        Teacher teacher = newTeacher();
        CourseCategory category = categories.saveAndFlush(new CourseCategory("EXR", "Exam Enrollment"));
        Course legacyCourse = saveCourse("Legacy Exam Course", "EXR101", teacher, category);
        legacyCourse.setStudents(new HashSet<>(List.of(student)));
        legacyCourse = courses.saveAndFlush(legacyCourse);
        Course registeredCourse = saveCourse("Registered Exam Course", "EXR102", teacher, category);
        Course droppedCourse = saveCourse("Dropped Exam Course", "EXR103", teacher, category);
        AcademicTerm term = saveTerm("EXR-AY", "EXR-T1");

        CourseRegistration registered = registration(student, saveSection(registeredCourse, term),
                RegistrationStatus.REGISTERED);
        CourseRegistration dropped = registration(student, saveSection(droppedCourse, term),
                RegistrationStatus.DROPPED);
        registrations.saveAllAndFlush(List.of(registered, dropped));

        exams.saveAllAndFlush(List.of(
                exam(legacyCourse.getId(), "Legacy exam"),
                exam(registeredCourse.getId(), "Registered exam"),
                exam(droppedCourse.getId(), "Dropped exam")));

        assertThat(exams.findForStudentByEnrolledCourses(student.getId(), RegistrationStatus.REGISTERED))
                .extracting(Exam::getTitle)
                .containsExactlyInAnyOrder("Legacy exam", "Registered exam");
    }

    private Student newStudent(String username, String registrationNumber) {
        Student student = new Student(new User(username, "encoded", Role.STUDENT), username, "Year 1");
        student.setRegistrationNumber(registrationNumber);
        return students.saveAndFlush(student);
    }

    private Teacher newTeacher() {
        return teachers.saveAndFlush(
                new Teacher(new User("academic-eligibility-teacher", "encoded", Role.TEACHER), "Eligibility Teacher"));
    }

    private Course saveCourse(String name, String code, Teacher teacher, CourseCategory category) {
        Course course = new Course(name, teacher);
        course.setCourseCode(code);
        course.setCategory(category);
        return courses.saveAndFlush(course);
    }

    private AcademicTerm saveTerm(String yearCode, String termCode) {
        AcademicYear year = new AcademicYear();
        year.setCode(yearCode);
        year.setName(yearCode + " Academic Year");
        year.setStartDate(LocalDate.of(2026, 1, 1));
        year.setEndDate(LocalDate.of(2026, 12, 31));
        year = academicYears.saveAndFlush(year);

        AcademicTerm term = new AcademicTerm();
        term.setCode(termCode);
        term.setName(termCode + " Academic Term");
        term.setAcademicYear(year);
        term.setStartDate(LocalDate.of(2026, 1, 1));
        term.setEndDate(LocalDate.of(2026, 6, 30));
        term.setRegistrationOpen(true);
        return terms.saveAndFlush(term);
    }

    private CourseSection saveSection(Course course, AcademicTerm term) {
        CourseSection section = new CourseSection();
        section.setCourse(course);
        section.setAcademicTerm(term);
        section.setSectionCode("A");
        section.setCapacity(30);
        return sections.saveAndFlush(section);
    }

    private CourseRegistration registration(Student student, CourseSection section, RegistrationStatus status) {
        CourseRegistration registration = new CourseRegistration();
        registration.setStudent(student);
        registration.setSection(section);
        registration.setStatus(status);
        return registration;
    }

    private Exam exam(Long courseId, String title) {
        Exam exam = new Exam();
        exam.setCourseId(courseId);
        exam.setTitle(title);
        exam.setStartsAt(LocalDateTime.of(2026, 6, 15, 9, 0));
        exam.setVenue("Main Hall");
        return exam;
    }
}
