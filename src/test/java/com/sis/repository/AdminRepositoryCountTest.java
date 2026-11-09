package com.sis.repository;

import com.sis.entity.Role;
import com.sis.entity.Student;
import com.sis.entity.Teacher;
import com.sis.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "sis.sample-data.enabled=false"
})
class AdminRepositoryCountTest {

    @Autowired
    private UserRepository users;

    @Autowired
    private StudentRepository students;

    @Autowired
    private TeacherRepository teachers;

    @Test
    void activeQueriesAndCountsAreScopedInTheDatabase() {
        Student activeStudent = saveStudent("active-student", true);
        saveStudent("inactive-student", false);
        saveTeacher("active-teacher", true);
        saveTeacher("inactive-teacher", false);

        assertThat(users.findByIsActiveTrueOrderByIdAsc())
                .extracting(User::getUsername)
                .containsExactly("active-student", "active-teacher");
        assertThat(users.findByIsActiveFalseOrderByIdAsc())
                .extracting(User::getUsername)
                .containsExactly("inactive-student", "inactive-teacher");
        Long authorId = users.findByUsername("active-student").orElseThrow().getId();
        assertThat(users.findActiveUserIdsExcluding(authorId))
                .containsExactly(users.findByUsername("active-teacher").orElseThrow().getId());
        assertThat(users.countByIsActiveTrue()).isEqualTo(2);
        assertThat(users.countByIsActiveFalse()).isEqualTo(2);
        assertThat(students.countByUserIsActiveTrue()).isEqualTo(1);
        assertThat(teachers.countByUserIsActiveTrue()).isEqualTo(1);
        assertThat(activeStudent.getUser().getIsActive()).isTrue();
    }

    private Student saveStudent(String username, boolean active) {
        User user = new User(username, "encoded", Role.STUDENT);
        user.setIsActive(active);
        Student student = new Student(user, username, "Year 1");
        student.setRegistrationNumber(username);
        return students.saveAndFlush(student);
    }

    private Teacher saveTeacher(String username, boolean active) {
        User user = new User(username, "encoded", Role.TEACHER);
        user.setIsActive(active);
        return teachers.saveAndFlush(new Teacher(user, username));
    }
}
