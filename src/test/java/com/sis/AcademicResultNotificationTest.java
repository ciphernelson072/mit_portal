package com.sis;

import com.sis.controller.AcademicController;
import com.sis.entity.ExamResult;
import com.sis.entity.NotificationCategory;
import com.sis.repository.ExamRepository;
import com.sis.repository.ExamResultRepository;
import com.sis.repository.AcademicTermRepository;
import com.sis.repository.TimetableEntryRepository;
import com.sis.service.ExamPermitService;
import com.sis.service.NotificationService;
import com.sis.service.PdfDocumentService;
import com.sis.service.SchoolService;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AcademicResultNotificationTest {

    @Test
    void publishedExamResultNotifiesStudentOnlyOnce() {
        SchoolService schoolService = mock(SchoolService.class);
        ExamResultRepository results = mock(ExamResultRepository.class);
        NotificationService notifications = mock(NotificationService.class);
        ExamResult result = new ExamResult();
        result.setStudentId(14L);
        when(results.findByIdForUpdate(8L)).thenReturn(Optional.of(result));
        when(results.save(result)).thenReturn(result);
        when(schoolService.getStudentUserId(14L)).thenReturn(Optional.of(42L));

        AcademicController controller = new AcademicController(
                schoolService,
                mock(TimetableEntryRepository.class),
                results,
                mock(AcademicTermRepository.class),
                mock(ExamRepository.class),
                mock(PdfDocumentService.class),
                mock(ExamPermitService.class),
                notifications,
                mock(com.sis.service.AcademicReportService.class));

        ResponseEntity<ExamResult> first = controller.publishExamResult(8L);
        ResponseEntity<ExamResult> repeated = controller.publishExamResult(8L);

        assertEquals(200, first.getStatusCodeValue());
        assertEquals(200, repeated.getStatusCodeValue());
        verify(notifications).create(42L, "Exam result available",
                "Your exam result is now available.", NotificationCategory.GRADE);
        verify(results, times(1)).save(result);
    }
}
