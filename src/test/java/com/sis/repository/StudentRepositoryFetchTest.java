package com.sis.repository;

import com.sis.entity.Role;
import com.sis.entity.Student;
import com.sis.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManagerFactory;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.jpa.open-in-view=false",
        "sis.sample-data.enabled=false"
})
class StudentRepositoryFetchTest {

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void findByUsernameWithCoursesReturnsCourseCollectionInitializedAfterRepositoryTransaction() {
        User user = new User("dashboard-student", "encoded-password", Role.STUDENT);
        Student student = new Student(user, "Dashboard Student", "Year 1");
        student.setRegistrationNumber("MIT/2026/00001");
        studentRepository.saveAndFlush(student);

        Student loadedStudent = studentRepository.findByUserUsernameWithCourses("dashboard-student").orElseThrow();

        assertThat(entityManagerFactory.getPersistenceUnitUtil().isLoaded(loadedStudent, "courses")).isTrue();
        assertThat(loadedStudent.getCourses()).isEmpty();
    }
}
