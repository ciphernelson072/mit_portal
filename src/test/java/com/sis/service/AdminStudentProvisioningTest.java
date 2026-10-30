package com.sis.service;

import com.sis.dto.CreateStudentRequest;
import com.sis.dto.CreateTeacherRequest;
import com.sis.dto.CreateUserRequest;
import com.sis.entity.Campus;
import com.sis.entity.Department;
import com.sis.entity.Faculty;
import com.sis.entity.AcademicYear;
import com.sis.entity.Program;
import com.sis.entity.Role;
import com.sis.entity.Student;
import com.sis.entity.StudentLifecycleStatus;
import com.sis.entity.StudentProgram;
import com.sis.entity.Teacher;
import com.sis.entity.User;
import com.sis.repository.CourseRepository;
import com.sis.repository.DepartmentRepository;
import com.sis.repository.GradeRepository;
import com.sis.repository.AcademicYearRepository;
import com.sis.repository.ProgramRepository;
import com.sis.repository.StudentProgramRepository;
import com.sis.repository.StudentRepository;
import com.sis.repository.StudentStatusHistoryRepository;
import com.sis.repository.TeacherRepository;
import com.sis.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.Map;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminStudentProvisioningTest {

    private final UserRepository users = mock(UserRepository.class);
    private final StudentRepository students = mock(StudentRepository.class);
    private final TeacherRepository teachers = mock(TeacherRepository.class);
    private final CourseRepository courses = mock(CourseRepository.class);
    private final DepartmentRepository departments = mock(DepartmentRepository.class);
    private final GradeRepository grades = mock(GradeRepository.class);
    private final StudentProgramRepository studentPrograms = mock(StudentProgramRepository.class);
    private final StudentStatusHistoryRepository statusHistory = mock(StudentStatusHistoryRepository.class);
    private final ProgramRepository programs = mock(ProgramRepository.class);
    private final AcademicYearRepository academicYears = mock(AcademicYearRepository.class);
    private final AcademicRecordsService academicRecords = mock(AcademicRecordsService.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final RefreshTokenService refreshTokens = mock(RefreshTokenService.class);
    private final AdminService service = new AdminService(
            users, students, teachers, courses, departments, grades, passwordEncoder, refreshTokens,
            studentPrograms, statusHistory, programs, academicYears, academicRecords);

    @Test
    void newAdminProvisionedStudentRequiresPasswordChange() {
        CreateStudentRequest request = new CreateStudentRequest();
        request.setUsername("new-student");
        request.setPassword("TemporaryPass123");
        request.setFullName("New Student");
        request.setEmail("new.student@example.test");
        request.setClassName("Bachelor of Science");
        when(users.findByUsernameIgnoreCase(request.getUsername())).thenReturn(Optional.empty());
        when(users.findByEmail(request.getEmail())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(request.getPassword())).thenReturn("encoded-password");
        when(users.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(students.save(any(Student.class))).thenAnswer(invocation -> {
            Student student = invocation.getArgument(0);
            student.setId(42L);
            return student;
        });

        service.admitStudent(request);

        org.mockito.ArgumentCaptor<User> savedUser = org.mockito.ArgumentCaptor.forClass(User.class);
        org.mockito.Mockito.verify(users).save(savedUser.capture());
        assertThat(savedUser.getValue().isPasswordChangeRequired()).isTrue();
    }

    @Test
    void standaloneCreationRejectsManagedStudentRoles() {
        CreateUserRequest request = new CreateUserRequest();
        request.setUsername("new-student");
        request.setPassword("TemporaryPass123");
        request.setRole(Role.STUDENT);
        when(users.findByUsernameIgnoreCase(request.getUsername())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(request.getPassword())).thenReturn("encoded-password");
        when(users.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThatThrownBy(() -> service.createUser(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("managed workflows");
        org.mockito.Mockito.verify(users, org.mockito.Mockito.never()).save(org.mockito.ArgumentMatchers.any(User.class));
    }

    @Test
    void hiringTeacherPersistsNormalizedHomeDepartment() {
        Campus campus = new Campus();
        campus.setId(3L);
        campus.setName("Main Campus");
        Faculty faculty = new Faculty();
        faculty.setId(5L);
        faculty.setName("Science Faculty");
        faculty.setCampus(campus);
        Department department = new Department();
        department.setId(7L);
        department.setName("Computer Science");
        department.setFaculty(faculty);

        CreateTeacherRequest request = new CreateTeacherRequest();
        request.setUsername("new-teacher");
        request.setPassword("TemporaryPass123");
        request.setFullName("New Teacher");
        request.setDepartmentId(department.getId());
        request.setDepartment("Software Engineering");
        when(departments.findById(department.getId())).thenReturn(Optional.of(department));
        when(departments.existsActiveInActiveFacultyAndCampus(department.getId())).thenReturn(true);
        when(users.findByUsernameIgnoreCase("new-teacher")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(request.getPassword())).thenReturn("encoded-password");
        when(users.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(teachers.save(any(Teacher.class))).thenAnswer(invocation -> {
            Teacher teacher = invocation.getArgument(0);
            teacher.setId(19L);
            return teacher;
        });

        var result = service.hireTeacher(request);

        org.mockito.ArgumentCaptor<Teacher> savedTeacher = org.mockito.ArgumentCaptor.forClass(Teacher.class);
        verify(teachers).save(savedTeacher.capture());
        assertThat(savedTeacher.getValue().getHomeDepartment()).isSameAs(department);
        assertThat(savedTeacher.getValue().getDepartment()).isEqualTo("Software Engineering");
        assertThat(result.getDepartmentId()).isEqualTo(7L);
        assertThat(result.getDepartmentName()).isEqualTo("Computer Science");
    }

    @Test
    void hiringTeacherRejectsDepartmentUnderInactiveHierarchyBeforeCreatingAccount() {
        CreateTeacherRequest request = new CreateTeacherRequest();
        request.setUsername("teacher-with-inactive-department");
        request.setPassword("TemporaryPass123");
        request.setFullName("Teacher With Inactive Department");
        request.setDepartmentId(7L);
        when(departments.findById(7L)).thenReturn(Optional.of(new Department()));
        when(departments.existsActiveInActiveFacultyAndCampus(7L)).thenReturn(false);

        assertThatThrownBy(() -> service.hireTeacher(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("active faculty and campus");
        verify(users, never()).save(any(User.class));
    }

    @Test
    void changingStudentStatusUpdatesSpecificEnrollmentAndWritesHistory() {
        Student student = new Student();
        student.setId(42L);
        StudentProgram enrollment = new StudentProgram();
        enrollment.setStatus(StudentLifecycleStatus.ACTIVE);
        when(students.findById(42L)).thenReturn(Optional.of(student));
        when(studentPrograms.findForUpdateByStudentIdAndProgramIdAndAcademicYearId(42L, 7L, 2026L))
                .thenReturn(Optional.of(enrollment));
        when(studentPrograms.save(any(StudentProgram.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(statusHistory.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.changeStudentLifecycleStatus(42L, 7L, 2026L, StudentLifecycleStatus.WITHDRAWN,
                "Student requested withdrawal", "admin");

        verify(studentPrograms).findForUpdateByStudentIdAndProgramIdAndAcademicYearId(42L, 7L, 2026L);
        assertThat(enrollment.getStatus()).isEqualTo(StudentLifecycleStatus.WITHDRAWN);
        assertThat(enrollment.getEndDate()).isNotNull();
        verify(studentPrograms).save(enrollment);
        org.mockito.ArgumentCaptor<com.sis.entity.StudentStatusHistory> history =
                org.mockito.ArgumentCaptor.forClass(com.sis.entity.StudentStatusHistory.class);
        verify(statusHistory).save(history.capture());
        assertThat(history.getValue().getStudent()).isSameAs(student);
        assertThat(history.getValue().getStudentProgram()).isSameAs(enrollment);
        assertThat(history.getValue().getStatus()).isEqualTo(StudentLifecycleStatus.WITHDRAWN);
        assertThat(history.getValue().getReason()).isEqualTo("Student requested withdrawal");
        assertThat(history.getValue().getChangedBy()).isEqualTo("admin");
    }

    @Test
    void reactivatingDeferredStudentClearsEndDate() {
        Student student = new Student();
        student.setId(42L);
        StudentProgram enrollment = new StudentProgram();
        enrollment.setStatus(StudentLifecycleStatus.DEFERRED);
        enrollment.setEndDate(java.time.LocalDate.now().minusDays(1));
        when(students.findById(42L)).thenReturn(Optional.of(student));
        when(studentPrograms.findForUpdateByStudentIdAndProgramIdAndAcademicYearId(42L, 7L, 2026L))
                .thenReturn(Optional.of(enrollment));

        service.changeStudentLifecycleStatus(42L, 7L, 2026L, StudentLifecycleStatus.ACTIVE,
                "Returned from deferral", "admin");

        assertThat(enrollment.getStatus()).isEqualTo(StudentLifecycleStatus.ACTIVE);
        assertThat(enrollment.getEndDate()).isNull();
    }

    @Test
    void lifecycleStatusChangeRejectsReactivationAfterGraduation() {
        Student student = new Student();
        student.setId(42L);
        StudentProgram enrollment = new StudentProgram();
        enrollment.setStatus(StudentLifecycleStatus.GRADUATED);
        when(students.findById(42L)).thenReturn(Optional.of(student));
        when(studentPrograms.findForUpdateByStudentIdAndProgramIdAndAcademicYearId(42L, 7L, 2026L))
                .thenReturn(Optional.of(enrollment));

        assertThatThrownBy(() -> service.changeStudentLifecycleStatus(42L, 7L, 2026L,
                StudentLifecycleStatus.ACTIVE, "Incorrect reactivation", "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("transition");
        verify(studentPrograms, never()).save(any(StudentProgram.class));
        verify(statusHistory, never()).save(any());
    }

    @Test
    void lifecycleStatusChangeRequiresMatchingEnrollment() {
        when(students.findById(42L)).thenReturn(Optional.of(new Student()));
        when(studentPrograms.findForUpdateByStudentIdAndProgramIdAndAcademicYearId(42L, 7L, 2026L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.changeStudentLifecycleStatus(42L, 7L, 2026L,
                StudentLifecycleStatus.SUSPENDED, "Suspension decision", "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No enrollment");
        verify(statusHistory, never()).save(any());
    }

    @Test
    void statusHistoryIncludesEnrollmentContextAndLegacyEntries() {
        StudentProgram enrollment = new StudentProgram();
        Program program = new Program();
        program.setName("Computer Science");
        AcademicYear year = new AcademicYear();
        year.setName("2026/2027");
        enrollment.setProgram(program);
        enrollment.setAcademicYear(year);

        com.sis.entity.StudentStatusHistory linked = new com.sis.entity.StudentStatusHistory();
        linked.setId(3L);
        linked.setStudentProgram(enrollment);
        linked.setStatus(StudentLifecycleStatus.TRANSFERRED);
        linked.setReason("Programme change");
        linked.setChangedBy("admin");
        linked.setChangedAt(java.time.LocalDateTime.parse("2026-10-19T09:30:00"));
        com.sis.entity.StudentStatusHistory legacy = new com.sis.entity.StudentStatusHistory();
        legacy.setStatus(StudentLifecycleStatus.ACTIVE);
        legacy.setChangedBy("admissions");
        when(students.existsById(42L)).thenReturn(true);
        when(statusHistory.findTop100ByStudentIdOrderByChangedAtDescIdDesc(42L))
                .thenReturn(List.of(linked, legacy));

        var history = service.getStudentStatusHistory(42L);

        assertThat(history).hasSize(2);
        assertThat(history.get(0).getProgramName()).isEqualTo("Computer Science");
        assertThat(history.get(0).getAcademicYearName()).isEqualTo("2026/2027");
        assertThat(history.get(0).getChangedBy()).isEqualTo("admin");
        assertThat(history.get(1).getProgramName()).isNull();
        assertThat(history.get(1).getAcademicYearName()).isNull();
    }

    @Test
    void graduationStatusRequiresPositiveDegreeAudit() {
        Student student = new Student();
        student.setId(42L);
        StudentProgram enrollment = new StudentProgram();
        enrollment.setStatus(StudentLifecycleStatus.ACTIVE);
        when(students.findById(42L)).thenReturn(Optional.of(student));
        when(studentPrograms.findForUpdateByStudentIdAndProgramIdAndAcademicYearId(42L, 7L, 2026L))
                .thenReturn(Optional.of(enrollment));
        when(academicRecords.degreeAudit(42L, 7L, 2026L))
                .thenReturn(Map.of("eligibleForGraduation", false));

        assertThatThrownBy(() -> service.changeStudentLifecycleStatus(42L, 7L, 2026L,
                StudentLifecycleStatus.GRADUATED, "Graduate", "admin"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Graduation requires");
        assertThat(enrollment.getStatus()).isEqualTo(StudentLifecycleStatus.ACTIVE);
        verify(studentPrograms, never()).save(any(StudentProgram.class));
        verify(statusHistory, never()).save(any());
    }

    @Test
    void lifecycleStatusChangeRequiresNonblankReasonAndAuthenticatedActor() {
        assertThatThrownBy(() -> service.changeStudentLifecycleStatus(42L, 7L, 2026L,
                StudentLifecycleStatus.SUSPENDED, "   ", "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reason");
        assertThatThrownBy(() -> service.changeStudentLifecycleStatus(42L, 7L, 2026L,
                StudentLifecycleStatus.SUSPENDED, "Documented decision", " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("authenticated administrator");
        verify(students, never()).findById(42L);
    }

    @Test
    void transferringStudentClosesSourceCreatesDestinationAndAuditsBothChanges() {
        User user = new User();
        user.setIsActive(true);
        Student student = new Student();
        student.setId(42L);
        student.setUser(user);
        StudentProgram source = new StudentProgram();
        source.setStatus(StudentLifecycleStatus.ACTIVE);
        Program destinationProgram = new Program();
        destinationProgram.setId(8L);
        destinationProgram.setCode("CS");
        destinationProgram.setName("Computer Science");
        Department destinationDepartment = new Department();
        destinationDepartment.setName("Computing");
        destinationProgram.setDepartment(destinationDepartment);
        AcademicYear destinationYear = new AcademicYear();
        destinationYear.setId(2026L);
        destinationYear.setName("2026/2027");
        destinationYear.setActive(true);

        when(students.findById(42L)).thenReturn(Optional.of(student));
        when(studentPrograms.findForUpdateByStudentIdAndProgramIdAndAcademicYearId(42L, 7L, 2025L))
                .thenReturn(Optional.of(source));
        when(programs.existsActiveInDirectory(8L)).thenReturn(true);
        when(programs.findByIdWithDirectory(8L)).thenReturn(Optional.of(destinationProgram));
        when(academicYears.findById(2026L)).thenReturn(Optional.of(destinationYear));
        when(studentPrograms.existsByStudentIdAndProgramIdAndAcademicYearId(42L, 8L, 2026L)).thenReturn(false);
        when(studentPrograms.save(any(StudentProgram.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(statusHistory.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.transferStudentProgram(42L, 7L, 2025L, 8L, 2026L,
                "Approved programme change", "admin");

        assertThat(source.getStatus()).isEqualTo(StudentLifecycleStatus.TRANSFERRED);
        assertThat(source.getEndDate()).isNotNull();
        org.mockito.ArgumentCaptor<StudentProgram> savedEnrollment =
                org.mockito.ArgumentCaptor.forClass(StudentProgram.class);
        org.mockito.Mockito.verify(studentPrograms, org.mockito.Mockito.times(2)).save(savedEnrollment.capture());
        StudentProgram destination = savedEnrollment.getAllValues().get(1);
        assertThat(destination.getStudent()).isSameAs(student);
        assertThat(destination.getProgram()).isSameAs(destinationProgram);
        assertThat(destination.getAcademicYear()).isSameAs(destinationYear);
        assertThat(destination.getStatus()).isEqualTo(StudentLifecycleStatus.ACTIVE);
        assertThat(destination.getStartDate()).isNotNull();

        org.mockito.ArgumentCaptor<com.sis.entity.StudentStatusHistory> history =
                org.mockito.ArgumentCaptor.forClass(com.sis.entity.StudentStatusHistory.class);
        verify(statusHistory, org.mockito.Mockito.times(2)).save(history.capture());
        assertThat(history.getAllValues()).extracting(com.sis.entity.StudentStatusHistory::getStatus)
                .containsExactly(StudentLifecycleStatus.TRANSFERRED, StudentLifecycleStatus.ACTIVE);
        assertThat(history.getAllValues()).extracting(com.sis.entity.StudentStatusHistory::getStudentProgram)
                .containsExactly(source, destination);
        assertThat(history.getAllValues()).allMatch(item -> "admin".equals(item.getChangedBy()));
    }

    @Test
    void transferRejectsInactiveDestinationYearBeforeChangingSource() {
        User user = new User();
        user.setIsActive(true);
        Student student = new Student();
        student.setId(42L);
        student.setUser(user);
        StudentProgram source = new StudentProgram();
        source.setStatus(StudentLifecycleStatus.ACTIVE);
        AcademicYear inactiveYear = new AcademicYear();
        inactiveYear.setId(2026L);
        inactiveYear.setActive(false);
        when(students.findById(42L)).thenReturn(Optional.of(student));
        when(studentPrograms.findForUpdateByStudentIdAndProgramIdAndAcademicYearId(42L, 7L, 2025L))
                .thenReturn(Optional.of(source));
        when(programs.existsActiveInDirectory(8L)).thenReturn(true);
        when(academicYears.findById(2026L)).thenReturn(Optional.of(inactiveYear));

        assertThatThrownBy(() -> service.transferStudentProgram(42L, 7L, 2025L, 8L, 2026L,
                "Approved programme change", "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be active");
        assertThat(source.getStatus()).isEqualTo(StudentLifecycleStatus.ACTIVE);
        verify(studentPrograms, never()).save(any(StudentProgram.class));
        verify(statusHistory, never()).save(any());
    }
}
