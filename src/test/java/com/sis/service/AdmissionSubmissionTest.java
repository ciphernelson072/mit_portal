package com.sis.service;

import com.sis.dto.AdmissionApplicationRequest;
import com.sis.entity.AcademicYear;
import com.sis.entity.AdmissionApplication;
import com.sis.entity.Program;
import com.sis.repository.AcademicYearRepository;
import com.sis.repository.AdmissionApplicationRepository;
import com.sis.repository.ProgramRepository;
import com.sis.repository.StudentProgramRepository;
import com.sis.repository.StudentRepository;
import com.sis.repository.StudentStatusHistoryRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdmissionSubmissionTest {
    private final AdmissionApplicationRepository applications = mock(AdmissionApplicationRepository.class);
    private final ProgramRepository programs = mock(ProgramRepository.class);
    private final AcademicYearRepository academicYears = mock(AcademicYearRepository.class);
    private final AdmissionService service = new AdmissionService(
            applications,
            programs,
            academicYears,
            mock(StudentRepository.class),
            mock(StudentProgramRepository.class),
            mock(StudentStatusHistoryRepository.class),
            mock(AdminService.class));

    @Test
    void storesTrimmedLowercaseEmailAndChecksCanonicalValue() {
        AdmissionApplicationRequest request = request("  Applicant+MIT@Example.Test ");
        AcademicYear year = new AcademicYear();
        year.setId(7L);
        Program program = new Program();
        program.setId(9L);
        program.setActive(true);
        when(academicYears.findById(7L)).thenReturn(Optional.of(year));
        when(applications.existsByNormalizedEmailAndAcademicYearId("applicant+mit@example.test", 7L))
                .thenReturn(false);
        when(programs.findById(9L)).thenReturn(Optional.of(program));
        when(programs.existsActiveInDirectory(9L)).thenReturn(true);
        when(applications.save(any(AdmissionApplication.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AdmissionApplication result = service.submit(request);

        assertThat(result.getEmail()).isEqualTo("applicant+mit@example.test");
        assertThat(result.getNormalizedEmail()).isEqualTo("applicant+mit@example.test");
        verify(applications).existsByNormalizedEmailAndAcademicYearId("applicant+mit@example.test", 7L);
    }

    @Test
    void rejectsAnExistingCanonicalEmailBeforeCreatingAnApplication() {
        AdmissionApplicationRequest request = request(" APPLICANT@example.test ");
        AcademicYear year = new AcademicYear();
        year.setId(7L);
        when(academicYears.findById(7L)).thenReturn(Optional.of(year));
        when(applications.existsByNormalizedEmailAndAcademicYearId("applicant@example.test", 7L))
                .thenReturn(true);

        assertThatThrownBy(() -> service.submit(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("An application already exists for this email and academic year");

        verify(applications, never()).save(any(AdmissionApplication.class));
    }

    @Test
    void rejectsAnActiveProgramWhenAnyParentDirectoryRecordIsInactive() {
        AdmissionApplicationRequest request = request("applicant@example.test");
        AcademicYear year = new AcademicYear();
        year.setId(7L);
        Program program = new Program();
        program.setId(9L);
        program.setActive(true);
        when(academicYears.findById(7L)).thenReturn(Optional.of(year));
        when(applications.existsByNormalizedEmailAndAcademicYearId("applicant@example.test", 7L))
                .thenReturn(false);
        when(programs.findById(9L)).thenReturn(Optional.of(program));
        when(programs.existsActiveInDirectory(9L)).thenReturn(false);

        assertThatThrownBy(() -> service.submit(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Active program not found");
        verify(applications, never()).save(any(AdmissionApplication.class));
    }

    private AdmissionApplicationRequest request(String email) {
        AdmissionApplicationRequest request = new AdmissionApplicationRequest();
        request.setApplicantName("Applicant Name");
        request.setEmail(email);
        request.setProgramId(9L);
        request.setAcademicYearId(7L);
        return request;
    }
}
