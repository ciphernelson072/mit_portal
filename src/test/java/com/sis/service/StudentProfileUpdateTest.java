package com.sis.service;

import com.sis.dto.UpdateStudentRequest;
import com.sis.entity.Role;
import com.sis.entity.Student;
import com.sis.entity.User;
import com.sis.repository.AssignmentRepository;
import com.sis.repository.AttendanceRepository;
import com.sis.repository.CourseCategoryRepository;
import com.sis.repository.CourseRepository;
import com.sis.repository.CourseUnitRepository;
import com.sis.repository.FeeRepository;
import com.sis.repository.GradeRepository;
import com.sis.repository.StudentRepository;
import com.sis.repository.TeacherRepository;
import com.sis.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StudentProfileUpdateTest {

    @Test
    void updatePersistsAllEditableProfileFields() {
        StudentRepository students = mock(StudentRepository.class);
        UserRepository users = mock(UserRepository.class);
        GradeRepository grades = mock(GradeRepository.class);
        User user = new User("student", "encoded", Role.STUDENT);
        user.setId(4L);
        user.setEmail("account@example.test");
        Student student = new Student(user, "Student Name", "Year One");
        student.setId(9L);
        when(users.findByUsernameIgnoreCase(anyString())).thenReturn(Optional.of(user));
        when(students.findByUserId(4L)).thenReturn(Optional.of(student));
        when(students.findById(9L)).thenReturn(Optional.of(student));
        when(grades.findByStudentIdAndStatus(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any())).thenReturn(Collections.emptyList());

        SchoolService service = new SchoolService(
                students, mock(TeacherRepository.class), mock(CourseRepository.class),
                grades, mock(CourseUnitRepository.class),
                mock(AttendanceRepository.class), users, mock(CourseCategoryRepository.class),
                mock(FeeRepository.class), mock(AssignmentRepository.class),
                mock(PasswordEncoder.class), mock(AcademicRecordService.class),
                mock(FeeService.class), mock(MessagingService.class),
                mock(CourseMaterialService.class));
        UpdateStudentRequest request = new UpdateStudentRequest();
        request.setFullName("Updated Student");
        request.setEmail("student@example.edu");
        request.setPhone("0700000000");
        request.setAddress("Campus Road");
        request.setDateOfBirth("2004-01-02");
        request.setGender("Female");
        request.setAlternativePhone("0711111111");
        request.setCity("Kampala");
        request.setCountry("Uganda");

        service.updateStudentProfileByUsername("student", request);

        assertThat(student.getDateOfBirth()).isEqualTo("2004-01-02");
        assertThat(student.getGender()).isEqualTo("Female");
        assertThat(student.getAlternativePhone()).isEqualTo("0711111111");
        assertThat(student.getCity()).isEqualTo("Kampala");
        assertThat(student.getCountry()).isEqualTo("Uganda");
        assertThat(student.getEmail()).isEqualTo("student@example.edu");
        assertThat(user.getEmail()).isEqualTo("account@example.test");
    }
}
