package com.sis.service;

import com.sis.dto.CreateScholarshipRequest;
import com.sis.entity.Scholarship;
import com.sis.entity.ScholarshipApplication;
import com.sis.repository.ScholarshipApplicationRepository;
import com.sis.repository.ScholarshipRepository;
import com.sis.repository.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class ScholarshipServiceTest {
    private ScholarshipRepository scholarships;
    private ScholarshipApplicationRepository applications;
    private ScholarshipService service;

    @BeforeEach
    void setUp() {
        scholarships = mock(ScholarshipRepository.class);
        applications = mock(ScholarshipApplicationRepository.class);
        service = new ScholarshipService(scholarships, applications, mock(StudentRepository.class));
    }

    @Test
    void applicationRequiresAnOpenScholarship() {
        Scholarship closed = scholarship(false);
        when(scholarships.findByIdForUpdate(4L)).thenReturn(Optional.of(closed));
        when(applications.findByStudentIdAndScholarshipId(9L, 4L)).thenReturn(Optional.empty());

        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> service.apply(4L, 9L));

        assertEquals(HttpStatus.CONFLICT, HttpStatus.valueOf(error.getStatusCode().value()));
        verify(applications, never()).save(any(ScholarshipApplication.class));
    }

    @Test
    void repeatedApplicationReturnsExistingRecordEvenAfterClosing() {
        ScholarshipApplication existing = new ScholarshipApplication(4L, 9L);
        when(scholarships.findByIdForUpdate(4L)).thenReturn(Optional.of(scholarship(false)));
        when(applications.findByStudentIdAndScholarshipId(9L, 4L)).thenReturn(Optional.of(existing));

        assertSame(existing, service.apply(4L, 9L));
        verify(applications, never()).save(any(ScholarshipApplication.class));
    }

    @Test
    void createsScholarshipOpenWithTrimmedText() {
        when(scholarships.save(any(Scholarship.class))).thenAnswer(invocation -> invocation.getArgument(0));
        CreateScholarshipRequest request = new CreateScholarshipRequest();
        request.setName("  Merit Award ");
        request.setDescription("  For excellent results ");
        request.setAmount(new BigDecimal("125000.00"));

        Scholarship result = service.create(request);

        assertEquals("Merit Award", result.getName());
        assertEquals("For excellent results", result.getDescription());
        assertEquals(new BigDecimal("125000.00"), result.getAmount());
        assertEquals(true, result.isOpen());
    }

    @Test
    void reviewFinalizesOnlySubmittedApplications() {
        ScholarshipApplication application = new ScholarshipApplication(4L, 9L);
        when(applications.findByIdForUpdate(12L)).thenReturn(Optional.of(application));
        when(applications.save(application)).thenReturn(application);

        ScholarshipApplication reviewed = service.reviewApplication(12L, "APPROVED");

        assertSame(application, reviewed);
        assertEquals("APPROVED", application.getStatus());
        application.setStatus("REJECTED");
        assertEquals(HttpStatus.CONFLICT,
                assertThrows(ResponseStatusException.class, () -> service.reviewApplication(12L, "APPROVED"))
                        .getStatusCode());
    }

    private static Scholarship scholarship(boolean open) {
        Scholarship scholarship = new Scholarship();
        scholarship.setOpen(open);
        return scholarship;
    }
}
