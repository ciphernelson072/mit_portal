package com.sis.repository;

import com.sis.entity.Course;
import com.sis.entity.CourseCategory;
import com.sis.entity.Grade;
import com.sis.entity.GradeStatus;
import com.sis.entity.Role;
import com.sis.entity.Student;
import com.sis.entity.Teacher;
import com.sis.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManagerFactory;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.jpa.open-in-view=false",
        "sis.sample-data.enabled=false"
})
class GradeRepositoryFetchTest {

    @Autowired
    private GradeRepository gradeRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private TeacherRepository teacherRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private CourseCategoryRepository courseCategoryRepository;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void pendingGradeQueryFiltersAndFetchesRelationsForAdminDtoMapping() {
        Student student = new Student(new User("pending-grade-student", "encoded", Role.STUDENT),
                "Pending Student", "Year 1");
        student.setRegistrationNumber("MIT/2026/00003");
        student = studentRepository.saveAndFlush(student);

        Teacher teacher = teacherRepository.saveAndFlush(
                new Teacher(new User("pending-grade-teacher", "encoded", Role.TEACHER), "Grade Teacher"));
        CourseCategory category = courseCategoryRepository.saveAndFlush(new CourseCategory("GRD", "Grades"));
        Course course = new Course("Grade Course", teacher);
        course.setCourseCode("GRD101");
        course.setCategory(category);
        course = courseRepository.saveAndFlush(course);

        Grade pending = new Grade(student, course, "B", null);
        pending.setStatus(GradeStatus.PENDING);
        gradeRepository.saveAndFlush(pending);
        Grade approved = new Grade(student, course, "A", null);
        approved.setStatus(GradeStatus.APPROVED);
        gradeRepository.saveAndFlush(approved);

        List<Grade> results = gradeRepository.findPendingWithStudentUserAndCourse(GradeStatus.PENDING);

        assertThat(results).hasSize(1);
        Grade result = results.get(0);
        assertThat(entityManagerFactory.getPersistenceUnitUtil().isLoaded(result, "student")).isTrue();
        assertThat(entityManagerFactory.getPersistenceUnitUtil().isLoaded(result, "course")).isTrue();
        assertThat(entityManagerFactory.getPersistenceUnitUtil().isLoaded(result.getStudent(), "user")).isTrue();
        assertThat(result.getStudent().getFullName()).isEqualTo("Pending Student");
        assertThat(result.getStudent().getUser().getUsername()).isEqualTo("pending-grade-student");
        assertThat(result.getCourse().getName()).isEqualTo("Grade Course");

        Grade reloaded = gradeRepository.findByIdWithStudentUserAndCourse(result.getId()).orElseThrow();
        assertThat(entityManagerFactory.getPersistenceUnitUtil().isLoaded(reloaded, "student")).isTrue();
        assertThat(entityManagerFactory.getPersistenceUnitUtil().isLoaded(reloaded, "course")).isTrue();
        assertThat(entityManagerFactory.getPersistenceUnitUtil().isLoaded(reloaded.getStudent(), "user")).isTrue();

        List<Grade> allGrades = gradeRepository.findAllWithStudentUserAndCourse();
        assertThat(allGrades).hasSize(2);
        allGrades.forEach(grade -> {
            assertThat(entityManagerFactory.getPersistenceUnitUtil().isLoaded(grade, "student")).isTrue();
            assertThat(entityManagerFactory.getPersistenceUnitUtil().isLoaded(grade, "course")).isTrue();
            assertThat(entityManagerFactory.getPersistenceUnitUtil().isLoaded(grade.getStudent(), "user")).isTrue();
        });
    }
}
