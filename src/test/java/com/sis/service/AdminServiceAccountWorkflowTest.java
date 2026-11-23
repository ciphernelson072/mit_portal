package com.sis.service;

import com.sis.dto.CreateUserRequest;
import com.sis.dto.UpdateUserRequest;
import com.sis.entity.Role;
import com.sis.entity.Student;
import com.sis.entity.User;
import com.sis.repository.EmailVerificationTokenRepository;
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
    private final org.springframework.context.ApplicationEventPublisher events =
            mock(org.springframework.context.ApplicationEventPublisher.class);
    private final EmailVerificationTokenRepository verificationTokens =
            mock(EmailVerificationTokenRepository.class);
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
            mock(AcademicRecordsService.class),
            mock(com.sis.repository.CurriculumCourseRepository.class),
            events,
            verificationTokens);

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
    void createsAdminAccountsWithPendingEmailVerification() {
        CreateUserRequest request = new CreateUserRequest();
        request.setUsername("account-admin");
        request.setPassword("secret12");
        request.setRole(Role.ADMIN);
        request.setEmail("owner@example.test");
        when(users.findByUsernameIgnoreCase("account-admin")).thenReturn(Optional.empty());
        when(users.findByEmailIgnoreCase("owner@example.test")).thenReturn(Optional.empty());
        when(verificationTokens.findByEmailIgnoreCase("owner@example.test")).thenReturn(Optional.empty());
        when(passwords.encode("secret12")).thenReturn("encoded");
        when(users.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(41L);
            return saved;
        });

        service.createUser(request);

        org.mockito.ArgumentCaptor<User> user = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(users).save(user.capture());
        assertThat(user.getValue().isEmailVerified()).isFalse();
        verify(events).publishEvent(new UserEmailVerificationRequestedEvent(41L, "owner@example.test"));
    }

    @Test
    void changingAnAccountEmailRequiresVerification() {
        User user = new User("account", "encoded", Role.ADMIN);
        user.setId(9L);
        user.setEmail("old@example.test");
        when(users.findById(9L)).thenReturn(Optional.of(user));
        when(users.findByEmailIgnoreCase("new@example.test")).thenReturn(Optional.empty());
        when(verificationTokens.findByEmailIgnoreCase("new@example.test")).thenReturn(Optional.empty());
        when(users.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        UpdateUserRequest request = new UpdateUserRequest();
        request.setEmail("new@example.test");

        service.updateUser(9L, request);

        assertThat(user.getEmail()).isEqualTo("new@example.test");
        assertThat(user.isEmailVerified()).isFalse();
        verify(events).publishEvent(new UserEmailVerificationRequestedEvent(9L, "new@example.test"));
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
