package com.sis;

import com.sis.controller.InstitutionAdminController;
import com.sis.dto.CreateAcademicTermRequest;
import com.sis.dto.UpdateAcademicTermRegistrationWindowRequest;
import com.sis.entity.AcademicTerm;
import com.sis.entity.AcademicYear;
import com.sis.repository.AcademicTermRepository;
import com.sis.repository.AcademicYearRepository;
import com.sis.repository.CampusRepository;
import com.sis.repository.DepartmentRepository;
import com.sis.repository.FacultyRepository;
import com.sis.repository.ProgramRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InstitutionAdminAcademicTermTest {
    private final AcademicYearRepository years = mock(AcademicYearRepository.class);
    private final AcademicTermRepository terms = mock(AcademicTermRepository.class);
    private final InstitutionAdminController controller = new InstitutionAdminController(
            mock(CampusRepository.class), mock(FacultyRepository.class), mock(DepartmentRepository.class),
            mock(ProgramRepository.class), years, terms);

    @Test
    void createsTermWithTrimmedValuesInsideAcademicYear() {
        AcademicYear year = academicYear();
        when(years.findById(5L)).thenReturn(Optional.of(year));
        when(terms.existsByCodeIgnoreCase("2026-T1")).thenReturn(false);
        when(terms.save(any(AcademicTerm.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = controller.createAcademicTerm(request());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody())
                .containsEntry("code", "2026-T1")
                .containsEntry("name", "Semester One")
                .containsEntry("academicYearId", 5L)
                .containsEntry("registrationOpen", true);
        verify(terms).save(any(AcademicTerm.class));
    }

    @Test
    void rejectsTermOutsideAcademicYear() {
        AcademicYear year = academicYear();
        when(years.findById(5L)).thenReturn(Optional.of(year));
        CreateAcademicTermRequest request = request();
        request.setStartDate(LocalDate.of(2025, 12, 20));

        assertThatThrownBy(() -> controller.createAcademicTerm(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("within the selected academic year");
        verify(terms, never()).save(any(AcademicTerm.class));
    }

    @Test
    void rejectsDuplicateTermCodeIgnoringCase() {
        when(years.findById(5L)).thenReturn(Optional.of(academicYear()));
        when(terms.existsByCodeIgnoreCase("2026-T1")).thenReturn(true);

        assertThatThrownBy(() -> controller.createAcademicTerm(request()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
        verify(terms, never()).save(any(AcademicTerm.class));
    }

    @Test
    void updatesTermRegistrationWindow() {
        AcademicTerm term = new AcademicTerm();
        term.setId(12L);
        term.setCode("2026-T1");
        term.setName("Semester One");
        term.setAcademicYear(academicYear());
        term.setStartDate(LocalDate.of(2026, 1, 1));
        term.setEndDate(LocalDate.of(2026, 6, 30));
        term.setRegistrationOpen(true);
        when(terms.findById(12L)).thenReturn(Optional.of(term));
        when(terms.save(term)).thenReturn(term);
        UpdateAcademicTermRegistrationWindowRequest request = new UpdateAcademicTermRegistrationWindowRequest();
        request.setRegistrationOpen(false);

        var response = controller.updateAcademicTermRegistrationWindow(12L, request);

        assertThat(response).containsEntry("registrationOpen", false);
        assertThat(term.getRegistrationOpen()).isFalse();
        verify(terms).save(term);
    }

    private AcademicYear academicYear() {
        AcademicYear year = new AcademicYear();
        year.setId(5L);
        year.setCode("2026");
        year.setName("Academic Year 2026");
        year.setStartDate(LocalDate.of(2026, 1, 1));
        year.setEndDate(LocalDate.of(2026, 12, 31));
        return year;
    }

    private CreateAcademicTermRequest request() {
        CreateAcademicTermRequest request = new CreateAcademicTermRequest();
        request.setCode(" 2026-T1 ");
        request.setName(" Semester One ");
        request.setAcademicYearId(5L);
        request.setStartDate(LocalDate.of(2026, 1, 1));
        request.setEndDate(LocalDate.of(2026, 6, 30));
        request.setRegistrationOpen(true);
        return request;
    }
}
