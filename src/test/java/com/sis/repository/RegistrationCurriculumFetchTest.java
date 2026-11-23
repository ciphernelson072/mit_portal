package com.sis.repository;

import com.sis.entity.AcademicTerm;
import com.sis.entity.AcademicYear;
import com.sis.entity.Campus;
import com.sis.entity.Course;
import com.sis.entity.CourseCategory;
import com.sis.entity.CoursePrerequisite;
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
import com.sis.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManagerFactory;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.jpa.open-in-view=false",
        "sis.sample-data.enabled=false"
})
class RegistrationCurriculumFetchTest {

    @Autowired
    private CourseSectionRepository sections;

    @Autowired
    private CourseRegistrationRepository registrations;

    @Autowired
    private CurriculumCourseRepository curriculum;

    @Autowired
    private CoursePrerequisiteRepository prerequisites;

    @Autowired
    private AcademicYearRepository academicYears;

    @Autowired
    private AcademicTermRepository terms;

    @Autowired
    private CourseRepository courses;

    @Autowired
    private CourseCategoryRepository categories;

    @Autowired
    private TeacherRepository teachers;

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
    private EntityManagerFactory entityManagerFactory;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void responseQueriesInitializeCourseTermProgramAndPrerequisiteFields() {
        AcademicYear year = new AcademicYear();
        year.setCode("FETCH-AY");
        year.setName("Fetch Academic Year");
        year.setStartDate(LocalDate.of(2026, 1, 1));
        year.setEndDate(LocalDate.of(2026, 12, 31));
        year = academicYears.saveAndFlush(year);

        AcademicTerm term = new AcademicTerm();
        term.setCode("FETCH-T1");
        term.setName("Fetch Term");
        term.setAcademicYear(year);
        term.setStartDate(LocalDate.of(2026, 1, 1));
        term.setEndDate(LocalDate.of(2026, 6, 30));
        terms.saveAndFlush(term);

        Teacher teacher = teachers.saveAndFlush(
                new Teacher(new User("registration-fetch-teacher", "encoded", Role.TEACHER), "Fetch Teacher"));
        CourseCategory category = categories.saveAndFlush(new CourseCategory("FETCH", "Fetch Category"));
        Course course = saveCourse("FETCH101", "Fetch Course", teacher, category);
        Course prerequisiteCourse = saveCourse("FETCH100", "Fetch Prerequisite", teacher, category);

        CourseSection section = new CourseSection();
        section.setCourse(course);
        section.setAcademicTerm(term);
        section.setSectionCode("FETCH-A");
        section.setCapacity(40);
        section = sections.saveAndFlush(section);

        com.sis.entity.Student student = students.saveAndFlush(
                new com.sis.entity.Student(
                        new User("registration-fetch-student", "encoded", Role.STUDENT),
                        "Fetch Student", "Year 1"));
        CourseRegistration registration = new CourseRegistration();
        registration.setStudent(student);
        registration.setSection(section);
        registration.setStatus(RegistrationStatus.REGISTERED);
        registration = registrations.saveAndFlush(registration);

        assertThat(courses.findByStudentIdWithTeacherAndCategoryIncludingRegistrations(student.getId()))
                .extracting(Course::getId).containsExactly(course.getId());
        assertThat(students.findByCourseIdIncludingRegistrations(course.getId()))
                .extracting(com.sis.entity.Student::getId).containsExactly(student.getId());

        Program program = saveProgram("FETCH");
        CurriculumCourse curriculumCourse = new CurriculumCourse();
        curriculumCourse.setProgram(program);
        curriculumCourse.setCourse(course);
        curriculumCourse.setSemesterNumber(1);
        curriculumCourse = curriculum.saveAndFlush(curriculumCourse);

        CoursePrerequisite prerequisite = new CoursePrerequisite();
        prerequisite.setCourse(course);
        prerequisite.setPrerequisiteCourse(prerequisiteCourse);
        prerequisite = prerequisites.saveAndFlush(prerequisite);

        List<CourseSection> listedSections = sections
                .findByAcademicTermIdAndActiveTrueOrderByCourseCourseCodeAsc(term.getId());
        assertThat(listedSections).hasSize(1);
        assertThat(entityManagerFactory.getPersistenceUnitUtil().isLoaded(listedSections.get(0), "course")).isTrue();
        assertThat(entityManagerFactory.getPersistenceUnitUtil().isLoaded(listedSections.get(0), "academicTerm")).isTrue();
        assertThat(listedSections.get(0).getCourse().getCourseCode()).isEqualTo("FETCH101");
        assertThat(listedSections.get(0).getAcademicTerm().getName()).isEqualTo("Fetch Term");
        assertThat(sections.findByIdWithCourseAndTerm(section.getId()).orElseThrow()
                .getCourse().getName()).isEqualTo("Fetch Course");

        List<CourseRegistration> studentRegistrations = registrations
                .findByStudentIdAndStatusOrderByRegisteredAtDesc(student.getId(), RegistrationStatus.REGISTERED);
        assertThat(studentRegistrations).hasSize(1);
        assertThat(entityManagerFactory.getPersistenceUnitUtil().isLoaded(studentRegistrations.get(0), "section")).isTrue();
        assertThat(studentRegistrations.get(0).getSection().getCourse().getCourseCode()).isEqualTo("FETCH101");
        assertThat(studentRegistrations.get(0).getSection().getAcademicTerm().getId()).isEqualTo(term.getId());
        assertThat(registrations.findByIdWithSectionCourseAndTerm(registration.getId()).orElseThrow()
                .getSection().getCourse().getName()).isEqualTo("Fetch Course");

        List<CurriculumCourse> curriculumCourses = curriculum
                .findByProgramIdOrderBySemesterNumberAscCourseCourseCodeAsc(program.getId());
        assertThat(curriculumCourses).hasSize(1);
        assertThat(curriculumCourses.get(0).getCourse().getCourseCode()).isEqualTo("FETCH101");
        assertThat(curriculumCourses.get(0).getProgram().getName()).isEqualTo("Fetch Program");
        assertThat(curriculum.findByIdWithProgramAndCourse(curriculumCourse.getId()).orElseThrow()
                .getCourse().getName()).isEqualTo("Fetch Course");

        List<CoursePrerequisite> coursePrerequisites = prerequisites.findByCourseId(course.getId());
        assertThat(coursePrerequisites).hasSize(1);
        assertThat(coursePrerequisites.get(0).getCourse().getCourseCode()).isEqualTo("FETCH101");
        assertThat(coursePrerequisites.get(0).getPrerequisiteCourse().getCourseCode()).isEqualTo("FETCH100");
        assertThat(prerequisites.findByIdWithCourses(prerequisite.getId()).orElseThrow()
                .getPrerequisiteCourse().getCourseCode()).isEqualTo("FETCH100");
    }

    @Test
    void automaticAssignmentQueryOnlyReturnsStudentsInTheRequestedSemester() {
        AcademicYear year = new AcademicYear();
        year.setCode("AUTO-AY");
        year.setName("Automation Academic Year");
        year.setStartDate(LocalDate.of(2026, 1, 1));
        year.setEndDate(LocalDate.of(2026, 12, 31));
        year = academicYears.saveAndFlush(year);
        AcademicYear otherYear = new AcademicYear();
        otherYear.setCode("AUTO-AY-OTHER");
        otherYear.setName("Other Automation Academic Year");
        otherYear.setStartDate(LocalDate.of(2027, 1, 1));
        otherYear.setEndDate(LocalDate.of(2027, 12, 31));
        otherYear = academicYears.saveAndFlush(otherYear);
        Program program = saveProgram("AUTO");
        Student matchingStudent = saveStudent("auto-match");
        Student otherSemesterStudent = saveStudent("auto-semester");
        Student otherYearStudent = saveStudent("auto-other-year");
        saveEnrollment(matchingStudent, program, year, 2);
        saveEnrollment(otherSemesterStudent, program, year, 3);
        saveEnrollment(otherYearStudent, program, otherYear, 2);
        Teacher teacher = teachers.saveAndFlush(
                new Teacher(new User("auto-assignment-teacher", "encoded", Role.TEACHER), "Automation Teacher"));
        CourseCategory category = categories.saveAndFlush(new CourseCategory("AUTO", "Automation Category"));
        Course requiredCourse = saveCourse("AUTO101", "Automation Required Course", teacher, category);
        CurriculumCourse requiredMapping = new CurriculumCourse();
        requiredMapping.setProgram(program);
        requiredMapping.setCourse(requiredCourse);
        requiredMapping.setSemesterNumber(2);
        requiredMapping.setRequired(true);
        curriculum.saveAndFlush(requiredMapping);

        assertThat(studentPrograms.findActiveForAutoAssignmentByProgramIdAndCurrentSemester(
                program.getId(), 2, StudentLifecycleStatus.ACTIVE))
                .extracting(enrollment -> enrollment.getStudent().getId())
                .containsExactlyInAnyOrder(matchingStudent.getId(), otherYearStudent.getId());
        assertThat(studentPrograms.findActiveForAutoAssignmentByStudentId(
                matchingStudent.getId(), StudentLifecycleStatus.ACTIVE))
                .extracting(enrollment -> enrollment.getStudent().getId())
                .containsExactly(matchingStudent.getId());
        assertThat(studentPrograms.findActiveForAutoAssignmentByCourseId(
                requiredCourse.getId(), StudentLifecycleStatus.ACTIVE))
                .extracting(enrollment -> enrollment.getStudent().getId())
                .containsExactlyInAnyOrder(matchingStudent.getId(), otherYearStudent.getId());
        assertThat(studentPrograms.findActiveForAutoAssignmentByTeacherId(
                teacher.getId(), StudentLifecycleStatus.ACTIVE))
                .extracting(enrollment -> enrollment.getStudent().getId())
                .containsExactlyInAnyOrder(matchingStudent.getId(), otherYearStudent.getId());
        assertThat(studentPrograms.findActiveForAutoAssignmentByCourseIdAndAcademicYearId(
                requiredCourse.getId(), year.getId(), StudentLifecycleStatus.ACTIVE))
                .extracting(enrollment -> enrollment.getStudent().getId())
                .containsExactly(matchingStudent.getId());
    }

    private Course saveCourse(String code, String name, Teacher teacher, CourseCategory category) {
        Course course = new Course(name, teacher);
        course.setCourseCode(code);
        course.setCategory(category);
        return courses.saveAndFlush(course);
    }

    private Program saveProgram(String prefix) {
        Campus campus = new Campus();
        campus.setCode(prefix + "-C");
        campus.setName("Fetch Campus");
        campus = campuses.saveAndFlush(campus);

        Faculty faculty = new Faculty();
        faculty.setCode(prefix + "-F");
        faculty.setName("Fetch Faculty");
        faculty.setCampus(campus);
        faculty = faculties.saveAndFlush(faculty);

        Department department = new Department();
        department.setCode(prefix + "-D");
        department.setName("Fetch Department");
        department.setFaculty(faculty);
        department = departments.saveAndFlush(department);

        Program program = new Program();
        program.setCode(prefix + "-P");
        program.setName("Fetch Program");
        program.setAwardTitle("Fetch Award");
        program.setDepartment(department);
        program.setDurationYears(3);
        return programs.saveAndFlush(program);
    }

    private Student saveStudent(String username) {
        User user = new User(username, "encoded", Role.STUDENT);
        user.setIsActive(true);
        return students.saveAndFlush(new Student(user, "Automation Student", "Year 1"));
    }

    private void saveEnrollment(Student student, Program program, AcademicYear year, int semester) {
        StudentProgram enrollment = new StudentProgram();
        enrollment.setStudent(student);
        enrollment.setProgram(program);
        enrollment.setAcademicYear(year);
        enrollment.setCurrentSemester(semester);
        enrollment.setStatus(StudentLifecycleStatus.ACTIVE);
        studentPrograms.saveAndFlush(enrollment);
    }
}
