package com.sis.service;

import com.sis.dto.AdminStudentDto;
import com.sis.dto.AdmissionEnrollmentCredentialsResponse;
import com.sis.dto.CreateAdmittedStudentRequest;
import com.sis.dto.CreateStudentRequest;
import com.sis.dto.EnrollAdmissionRequest;
import com.sis.dto.AdmissionStatusRequest;
import com.sis.entity.AcademicYear;
import com.sis.entity.AdmissionApplication;
import com.sis.entity.AdmissionApplicationStatus;
import com.sis.entity.Program;
import com.sis.entity.Student;
import com.sis.entity.StudentProgram;
import com.sis.entity.User;
import com.sis.repository.AcademicYearRepository;
import com.sis.repository.AdmissionApplicationRepository;
import com.sis.repository.ProgramRepository;
import com.sis.repository.StudentProgramRepository;
import com.sis.repository.StudentRepository;
import com.sis.repository.StudentStatusHistoryRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdmissionEnrollmentTest {

    private final AdmissionApplicationRepository applications = mock(AdmissionApplicationRepository.class);
    private final ProgramRepository programs = mock(ProgramRepository.class);
    private final AcademicYearRepository academicYears = mock(AcademicYearRepository.class);
    private final StudentRepository students = mock(StudentRepository.class);
    private final StudentProgramRepository studentPrograms = mock(StudentProgramRepository.class);
    private final StudentStatusHistoryRepository statusHistory = mock(StudentStatusHistoryRepository.class);
    private final AdminService adminService = mock(AdminService.class);
    private final AdmissionService service = new AdmissionService(
            applications, programs, academicYears, students, studentPrograms, statusHistory, adminService);

    @Test
    void onlyEnrollsAnActiveStudentWhoseEmailMatchesTheAcceptedApplication() {
        AdmissionApplication application = acceptedApplication();
        Student student = student("different@example.com");
        when(applications.findByIdWithProgramAndAcademicYear(8L)).thenReturn(Optional.of(application));
        when(programs.existsActiveInDirectory(5L)).thenReturn(true);
        when(students.findById(19L)).thenReturn(Optional.of(student));
        EnrollAdmissionRequest request = new EnrollAdmissionRequest();
        request.setStudentId(19L);

        assertThatThrownBy(() -> service.enroll(8L, request, "registrar"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Student email must match the accepted applicant's email");

        verify(studentPrograms, never()).save(any(StudentProgram.class));
        verify(statusHistory, never()).save(any());
    }

    @Test
    void enrollmentIsIdempotentForTheSameStudentProgrammeAndAcademicYear() {
        AdmissionApplication application = acceptedApplication();
        Student student = student("applicant@example.com");
        StudentProgram existing = new StudentProgram();
        existing.setId(33L);
        when(applications.findByIdWithProgramAndAcademicYear(8L)).thenReturn(Optional.of(application));
        when(programs.existsActiveInDirectory(5L)).thenReturn(true);
        when(students.findById(19L)).thenReturn(Optional.of(student));
        when(studentPrograms.findByStudentIdAndProgramIdAndAcademicYearId(19L, 5L, 2L))
                .thenReturn(Optional.of(existing));
        EnrollAdmissionRequest request = new EnrollAdmissionRequest();
        request.setStudentId(19L);

        StudentProgram result = service.enroll(8L, request, "registrar");

        assertThat(result).isSameAs(existing);
        verify(studentPrograms, never()).save(any(StudentProgram.class));
        verify(statusHistory, never()).save(any());
    }

    @Test
    void reportsWhetherAnApplicantHasAnEnrollmentForTheirProgrammeAndYear() {
        AdmissionApplication application = acceptedApplication();
        when(studentPrograms.existsByProgrammeYearAndStudentEmail(
                5L, 2L, "applicant@example.com")).thenReturn(true);

        assertThat(service.isEnrolled(application)).isTrue();
    }

    @Test
    void retrievesEnrolledApplicationIdsInOneRepositoryCall() {
        when(applications.findEnrolledApplicationIds()).thenReturn(List.of(8L, 12L));

        assertThat(service.enrolledApplicationIds()).isEqualTo(Set.of(8L, 12L));
        verify(applications).findEnrolledApplicationIds();
    }

    @Test
    void createsAndEnrollsAnAcceptedApplicantWithinTheAdmissionServiceFlow() {
        AdmissionApplication application = acceptedApplication();
        Student student = student("applicant@example.com");
        CreateAdmittedStudentRequest request = new CreateAdmittedStudentRequest();
        request.setUsername("applicant");
        request.setFullName("Applicant Name");
        request.setEmail("APPLICANT@example.com");
        AdminStudentDto created = new AdminStudentDto();
        created.setId(19L);
        created.setUsername("applicant");

        when(applications.findByIdWithProgramAndAcademicYear(8L)).thenReturn(Optional.of(application));
        when(programs.existsActiveInDirectory(5L)).thenReturn(true);
        when(adminService.admitStudent(any(CreateStudentRequest.class))).thenReturn(created);
        when(students.findById(19L)).thenReturn(Optional.of(student));
        when(studentPrograms.save(any(StudentProgram.class))).thenAnswer(invocation -> {
            StudentProgram saved = invocation.getArgument(0);
            saved.setId(77L);
            return saved;
        });

        AdmissionEnrollmentCredentialsResponse result =
                service.createStudentAndEnroll(8L, request, "registrar");

        assertThat(result.getId()).isEqualTo(77L);
        assertThat(result.getStudentId()).isEqualTo(19L);
        assertThat(result.getProgramId()).isEqualTo(5L);
        assertThat(result.getAcademicYearId()).isEqualTo(2L);
        assertThat(result.getStatus()).isEqualTo("ACTIVE");
        assertThat(result.getUsername()).isEqualTo("applicant");
        assertThat(result.getTemporaryPassword())
                .matches("[A-Za-z0-9_-]{32}")
                .hasSizeBetween(8, 100);
        assertThat(request.getEmail()).isEqualTo("APPLICANT@example.com");
        org.mockito.ArgumentCaptor<CreateStudentRequest> studentRequest =
                org.mockito.ArgumentCaptor.forClass(CreateStudentRequest.class);
        verify(adminService).admitStudent(studentRequest.capture());
        assertThat(studentRequest.getValue().getEmail()).isEqualTo(application.getEmail());
        assertThat(studentRequest.getValue().getPhone()).isEqualTo(application.getPhone());
        assertThat(studentRequest.getValue().getClassName()).isEqualTo(application.getProgram().getName());
        assertThat(studentRequest.getValue().getPassword()).isEqualTo(result.getTemporaryPassword());
        verify(statusHistory).save(any());
    }

    @Test
    void doesNotCreateAnAccountWhenTheSubmittedEmailDoesNotMatchTheApplicant() {
        when(applications.findByIdWithProgramAndAcademicYear(8L)).thenReturn(Optional.of(acceptedApplication()));
        when(programs.existsActiveInDirectory(5L)).thenReturn(true);
        CreateAdmittedStudentRequest request = new CreateAdmittedStudentRequest();
        request.setEmail("someone-else@example.com");

        assertThatThrownBy(() -> service.createStudentAndEnroll(8L, request, "registrar"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Student email must match the accepted applicant's email");

        verify(adminService, never()).admitStudent(any(CreateStudentRequest.class));
    }

    @Test
    void doesNotCreateAnAccountWhenAcceptedProgrammeDirectoryIsInactive() {
        when(applications.findByIdWithProgramAndAcademicYear(8L))
                .thenReturn(Optional.of(acceptedApplication()));
        when(programs.existsActiveInDirectory(5L)).thenReturn(false);
        CreateAdmittedStudentRequest request = new CreateAdmittedStudentRequest();
        request.setUsername("applicant");
        request.setFullName("Applicant Name");
        request.setEmail("applicant@example.com");

        assertThatThrownBy(() -> service.createStudentAndEnroll(8L, request, "registrar"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("campus, faculty, department, or programme is inactive");
        verify(adminService, never()).admitStudent(any(CreateStudentRequest.class));
    }

    @Test
    void propagatesEnrollmentFailuresAfterStudentCreation() {
        AdmissionApplication application = acceptedApplication();
        Student student = student("applicant@example.com");
        CreateAdmittedStudentRequest request = new CreateAdmittedStudentRequest();
        request.setUsername("applicant");
        request.setFullName("Applicant Name");
        request.setEmail("APPLICANT@example.com");
        AdminStudentDto created = new AdminStudentDto();
        created.setId(19L);

        when(applications.findByIdWithProgramAndAcademicYear(8L)).thenReturn(Optional.of(application));
        when(programs.existsActiveInDirectory(5L)).thenReturn(true);
        when(adminService.admitStudent(any(CreateStudentRequest.class))).thenReturn(created);
        when(students.findById(19L)).thenReturn(Optional.of(student));
        when(studentPrograms.save(any(StudentProgram.class))).thenThrow(new RuntimeException("database write failed"));

        assertThatThrownBy(() -> service.createStudentAndEnroll(8L, request, "registrar"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("database write failed");

        verify(statusHistory, never()).save(any());
    }

    @Test
    void statusLookupRequiresApplicationNumberAndMatchingEmail() {
        AdmissionStatusRequest request = new AdmissionStatusRequest();
        request.setApplicationNumber("APP-8");
        request.setEmail("applicant@example.com");
        AdmissionApplication application = acceptedApplication();
        when(applications.findByApplicationNumberAndEmail("APP-8", "applicant@example.com"))
                .thenReturn(Optional.of(application));

        assertThat(service.findStatus(request)).isSameAs(application);
    }

    @Test
    void statusLookupUsesOneGenericErrorForUnknownNumberOrWrongEmail() {
        AdmissionStatusRequest request = new AdmissionStatusRequest();
        request.setApplicationNumber("APP-8");
        request.setEmail("wrong@example.com");

        assertThatThrownBy(() -> service.findStatus(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Application number or email address is incorrect");
    }

    private AdmissionApplication acceptedApplication() {
        Program program = new Program();
        program.setId(5L);
        AcademicYear year = new AcademicYear();
        year.setId(2L);
        AdmissionApplication application = new AdmissionApplication();
        application.setId(8L);
        application.setEmail("applicant@example.com");
        application.setPhone("555-0100");
        application.setProgram(program);
        application.setAcademicYear(year);
        application.setStatus(AdmissionApplicationStatus.ACCEPTED);
        return application;
    }

    private Student student(String email) {
        User user = new User();
        user.setIsActive(true);
        Student student = new Student();
        student.setId(19L);
        student.setEmail(email);
        student.setUser(user);
        return student;
    }
}
