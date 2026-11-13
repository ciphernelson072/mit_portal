package com.sis.repository;

import com.sis.entity.Attendance;
import com.sis.entity.Course;
import com.sis.entity.CourseCategory;
import com.sis.entity.CourseUnit;
import com.sis.entity.Role;
import com.sis.entity.Student;
import com.sis.entity.Teacher;
import com.sis.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManagerFactory;
import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.jpa.open-in-view=false",
        "sis.sample-data.enabled=false"
})
class AttendanceRepositoryFetchTest {

    @Autowired
    private AttendanceRepository attendanceRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private TeacherRepository teacherRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private CourseCategoryRepository courseCategoryRepository;

    @Autowired
    private CourseUnitRepository courseUnitRepository;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void attendanceReadQueriesInitializeStudentAndCourseForDtoMapping() {
        Student student = new Student(new User("attendance-student", "encoded", Role.STUDENT),
                "Attendance Student", "Year 1");
        student.setRegistrationNumber("MIT/2026/00002");
        student = studentRepository.saveAndFlush(student);

        Teacher teacher = teacherRepository.saveAndFlush(
                new Teacher(new User("attendance-teacher", "encoded", Role.TEACHER), "Attendance Teacher"));
        CourseCategory category = courseCategoryRepository.saveAndFlush(new CourseCategory("ATT", "Attendance"));
        Course course = new Course("Attendance Course", teacher);
        course.setCourseCode("ATT101");
        course.setCategory(category);
        course.setStudents(Set.of(student));
        course = courseRepository.saveAndFlush(course);
        CourseUnit unit = new CourseUnit();
        unit.setUnitCode("ATT101A");
        unit.setName("Attendance Unit");
        unit.setCourse(course);
        unit.setTeacher(teacher);
        unit = courseUnitRepository.saveAndFlush(unit);

        LocalDate date = LocalDate.of(2026, 10, 6);
        Attendance attendance = new Attendance(student, course, date, true);
        attendanceRepository.saveAndFlush(attendance);

        Attendance byCourseAndDate = attendanceRepository
                .findByCourseIdAndDateWithStudentAndCourse(course.getId(), date).get(0);
        Attendance byStudent = attendanceRepository
                .findByStudentIdWithCourse(student.getId()).get(0);
        Attendance allAttendance = attendanceRepository.findAllWithStudentAndCourse().get(0);

        assertAttendanceRelationsLoaded(byCourseAndDate);
        assertAttendanceRelationsLoaded(byStudent);
        assertAttendanceRelationsLoaded(allAttendance);
        assertThat(courseUnitRepository.existsAssignedToTeacherForCourse(unit.getId(), "attendance-teacher")).isTrue();
        assertThat(courseUnitRepository.existsAssignedToTeacherForCourse(unit.getId(), "another-teacher")).isFalse();
    }

    private void assertAttendanceRelationsLoaded(Attendance attendance) {
        assertThat(entityManagerFactory.getPersistenceUnitUtil().isLoaded(attendance, "student")).isTrue();
        assertThat(entityManagerFactory.getPersistenceUnitUtil().isLoaded(attendance, "course")).isTrue();
        assertThat(attendance.getStudent().getFullName()).isEqualTo("Attendance Student");
        assertThat(attendance.getCourse().getName()).isEqualTo("Attendance Course");
    }
}
