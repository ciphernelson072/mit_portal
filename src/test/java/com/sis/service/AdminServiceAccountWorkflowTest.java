package com.sis.service;

import com.sis.dto.CreateUserRequest;
import com.sis.dto.UpdateUserRequest;
import com.sis.entity.Role;
import com.sis.entity.Student;
import com.sis.entity.User;
import com.sis.repository.AcademicYearRepository;
import com.sis.repository.CourseRepository;
import com.sis.repository.DepartmentRepository;
import com.sis.repository.GradeRepository;
import com.sis.repository.ProgramRepository;
import com.sis.repository.StudentProgramRepository;
import com.sis.repository.StudentRepository;
import com.sis.repository.StudentStatusHistoryRepository;
import com.sis.repository.TeacherRepository;
import com.sis.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminServiceAccountWorkflowTest {

    private final UserRepository users = mock(UserRepository.class);
    private final StudentRepository students = mock(StudentRepository.class);
    private final TeacherRepository teachers = mock(TeacherRepository.class);
    private final PasswordEncoder passwords = mock(PasswordEncoder.class);
    private final RefreshTokenService refreshTokens = mock(RefreshTokenService.class);
    private final AdminService service = new AdminService(
            users,
            students,
            teachers,
            mock(CourseRepository.class),
            mock(DepartmentRepository.class),
            mock(GradeRepository.class),
            passwords,
            refreshTokens,
            mock(StudentProgramRepository.class),
            mock(StudentStatusHistoryRepository.class),
            mock(ProgramRepository.class),
            mock(AcademicYearRepository.class),
            mock(AcademicRecordsService.class));

    @Test
    void managedRolesCannotBeCreatedThroughStandaloneAdminAccountCreation() {
        CreateUserRequest request = new CreateUserRequest();
        request.setUsername("student-account");
        request.setPassword("secret12");
        request.setRole(Role.STUDENT);

        assertThatThrownBy(() -> service.createUser(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("managed workflows");
        verify(users, org.mockito.Mockito.never()).save(any(User.class));
    }

    @Test
    void studentRoleRequiresAnExistingStudentProfile() {
        User user = new User("account", "encoded", Role.ADMIN);
        user.setId(9L);
        when(users.findById(9L)).thenReturn(Optional.of(user));
        when(users.countByRoleAndIsActive(Role.ADMIN, true)).thenReturn(2L);
        when(students.findByUserId(9L)).thenReturn(Optional.empty());
        UpdateUserRequest request = new UpdateUserRequest();
        request.setRole(Role.STUDENT);

        assertThatThrownBy(() -> service.updateUser(9L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no student profile");
    }

    @Test
    void changingToStudentRoleRequiresPasswordChangeAtNextLogin() {
        User user = new User("account", "encoded", Role.ADMIN);
        user.setId(9L);
        when(users.findById(9L)).thenReturn(Optional.of(user));
        when(users.countByRoleAndIsActive(Role.ADMIN, true)).thenReturn(2L);
        when(students.findByUserId(9L)).thenReturn(Optional.of(new Student()));
        when(users.save(user)).thenReturn(user);
        UpdateUserRequest request = new UpdateUserRequest();
        request.setRole(Role.STUDENT);

        service.updateUser(9L, request);

        assertThat(user.getRole()).isEqualTo(Role.STUDENT);
        assertThat(user.isPasswordChangeRequired()).isTrue();
        verify(refreshTokens).revokeAllForUser(9L);
    }
}
