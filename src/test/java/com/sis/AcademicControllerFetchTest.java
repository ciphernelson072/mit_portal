package com.sis;

import com.sis.controller.AcademicController;
import com.sis.entity.Course;
import com.sis.entity.Exam;
import com.sis.entity.RegistrationStatus;
import com.sis.entity.Student;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AcademicControllerFetchTest {

    @AfterEach
    void clearSecurityContext() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    @Test
    void studentTimetableLoadsStudentWithCoursesBeforeReadingCourseIds() {
        SchoolService schoolService = mock(SchoolService.class);
        TimetableEntryRepository timetable = mock(TimetableEntryRepository.class);
        Student student = new Student();
        student.setId(7L);
        Course course = new Course();
        course.setId(19L);
        student.setCourses(Set.of(course));
        when(schoolService.getStudentWithCoursesByUsername("academic-student"))
                .thenReturn(Optional.of(student));
        when(timetable.findCurrentRegisteredEntries(
                7L, RegistrationStatus.REGISTERED, LocalDate.now()))
                .thenReturn(List.of(new TimetableEntry()));
        when(timetable.findByCourseIdInAndAcademicTermIdIsNull(List.of(19L)))
                .thenReturn(List.of(new TimetableEntry()));
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("academic-student", "n/a",
                        List.of(new SimpleGrantedAuthority("STUDENT"))));
        AcademicController controller = new AcademicController(
                schoolService, timetable,
                mock(ExamResultRepository.class), mock(AcademicTermRepository.class), mock(ExamRepository.class),
                mock(PdfDocumentService.class), mock(ExamPermitService.class), mock(NotificationService.class),
                mock(com.sis.service.AcademicReportService.class));

        assertEquals(2, controller.studentTimetable().getBody().size());
        verify(schoolService).getStudentWithCoursesByUsername("academic-student");
        verify(timetable).findByCourseIdInAndAcademicTermIdIsNull(List.of(19L));
        verify(timetable).findCurrentRegisteredEntries(
                7L, RegistrationStatus.REGISTERED, LocalDate.now());
    }

    @Test
    void studentHallTicketIncludesPermittedExamsFromRegisteredSections() throws Exception {
        SchoolService schoolService = mock(SchoolService.class);
        ExamRepository exams = mock(ExamRepository.class);
        PdfDocumentService pdfDocuments = mock(PdfDocumentService.class);
        ExamPermitService examPermits = mock(ExamPermitService.class);
        Student student = new Student();
        student.setId(7L);
        Exam exam = new Exam();
        exam.setCourseId(19L);
        exam.setTitle("Registered Course Exam");
        when(schoolService.getStudentWithCoursesByUsername("academic-student"))
                .thenReturn(Optional.of(student));
        when(exams.findForStudentByEnrolledCourses(7L, RegistrationStatus.REGISTERED))
                .thenReturn(List.of(exam));
        when(examPermits.hasApprovedPermit(7L, 19L)).thenReturn(true);
        when(pdfDocuments.hallTicket(List.of(exam))).thenReturn(new byte[]{1, 2, 3});
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("academic-student", "n/a",
                        List.of(new SimpleGrantedAuthority("STUDENT"))));
        AcademicController controller = new AcademicController(
                schoolService, mock(TimetableEntryRepository.class),
                mock(ExamResultRepository.class), mock(AcademicTermRepository.class), exams,
                pdfDocuments, examPermits, mock(NotificationService.class),
                mock(com.sis.service.AcademicReportService.class));
        ReflectionTestUtils.setField(exam, "id", 19L);

        var response = controller.hallTicket();

        assertEquals(200, response.getStatusCode().value());
        org.junit.jupiter.api.Assertions.assertArrayEquals(new byte[]{1, 2, 3}, response.getBody());
        verify(exams).findForStudentByEnrolledCourses(7L, RegistrationStatus.REGISTERED);
        verify(examPermits).hasApprovedPermit(7L, 19L);
    }
}
