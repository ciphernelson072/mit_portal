package com.sis;

import com.sis.controller.AcademicController;
import com.sis.dto.CreateTimetableRequest;
import com.sis.entity.AcademicTerm;
import com.sis.entity.Course;
import com.sis.entity.TimetableEntry;
import com.sis.repository.AcademicTermRepository;
import com.sis.repository.ExamRepository;
import com.sis.repository.ExamResultRepository;
import com.sis.repository.TimetableEntryRepository;
import com.sis.service.ExamPermitService;
import com.sis.service.NotificationService;
import com.sis.service.PdfDocumentService;
import com.sis.service.SchoolService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AcademicTimetableConflictTest {
    private final SchoolService schoolService = mock(SchoolService.class);
    private final TimetableEntryRepository timetable = mock(TimetableEntryRepository.class);
    private final AcademicTermRepository terms = mock(AcademicTermRepository.class);
    private final AcademicController controller = new AcademicController(
            schoolService, timetable,
            mock(ExamResultRepository.class), terms, mock(ExamRepository.class),
            mock(PdfDocumentService.class), mock(ExamPermitService.class),
            mock(NotificationService.class), mock(com.sis.service.AcademicReportService.class));

    @AfterEach
    void clearSecurityContext() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    @Test
    void rejectsOverlappingBookingForSameVenueAndTerm() {
        authenticateAsAdmin();
        when(schoolService.getCourseById(10L)).thenReturn(Optional.of(course(10L)));
        when(terms.findByIdForUpdate(3L)).thenReturn(Optional.of(term(3L)));
        when(timetable.findByDayOfWeekIgnoreCase("monday"))
                .thenReturn(List.of(entry(3L, "  room 1 ", "09:00", "10:00")));

        assertThatThrownBy(() -> controller.createTimetable(request(10L, 3L, "09:30", "10:30")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Venue is already booked");
        verify(timetable, never()).save(any(TimetableEntry.class));
    }

    @Test
    void permitsSameVenueAtTouchingBoundaryAndInDifferentTerm() {
        authenticateAsAdmin();
        when(schoolService.getCourseById(10L)).thenReturn(Optional.of(course(10L)));
        when(terms.findByIdForUpdate(3L)).thenReturn(Optional.of(term(3L)));
        when(timetable.findByDayOfWeekIgnoreCase("monday"))
                .thenReturn(List.of(entry(3L, "Room 1", "09:00", "10:00"),
                        entry(4L, "Room 1", "09:00", "11:00")));
        when(timetable.save(any(TimetableEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = controller.createTimetable(request(10L, 3L, "10:00", "11:00"));

        assertThat(response.getStatusCodeValue()).isEqualTo(201);
        assertThat(response.getBody().getAcademicTermId()).isEqualTo(3L);
        assertThat(response.getBody().getDayOfWeek()).isEqualTo("MONDAY");
        verify(timetable).save(any(TimetableEntry.class));
    }

    private void authenticateAsAdmin() {
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin", "n/a",
                        List.of(new SimpleGrantedAuthority("ADMIN"))));
    }

    private Course course(Long id) {
        Course course = new Course();
        course.setId(id);
        return course;
    }

    private AcademicTerm term(Long id) {
        AcademicTerm term = new AcademicTerm();
        term.setId(id);
        return term;
    }

    private TimetableEntry entry(Long termId, String venue, String start, String end) {
        TimetableEntry entry = new TimetableEntry();
        entry.setAcademicTermId(termId);
        entry.setDayOfWeek("MONDAY");
        entry.setVenue(venue);
        entry.setStartsAt(LocalTime.parse(start));
        entry.setEndsAt(LocalTime.parse(end));
        return entry;
    }

    private CreateTimetableRequest request(Long courseId, Long termId, String start, String end) {
        CreateTimetableRequest request = new CreateTimetableRequest();
        request.setCourseId(courseId);
        request.setAcademicTermId(termId);
        request.setDayOfWeek(" monday ");
        request.setStartsAt(LocalTime.parse(start));
        request.setEndsAt(LocalTime.parse(end));
        request.setVenue("Room 1");
        return request;
    }
}
