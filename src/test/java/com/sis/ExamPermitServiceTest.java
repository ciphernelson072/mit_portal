package com.sis;

import com.sis.entity.Exam;
import com.sis.entity.ExamPermit;
import com.sis.entity.ExamPermitStatus;
import com.sis.entity.User;
import com.sis.repository.AuditLogRepository;
import com.sis.repository.ExamPermitRepository;
import com.sis.repository.ExamRepository;
import com.sis.repository.UserRepository;
import com.sis.service.ExamPermitService;
import com.sis.service.SchoolService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExamPermitServiceTest {

    @Test
    void requestAndApprovePermitTracksStandardUniversityWorkflow() {
        ExamPermitRepository repository = Mockito.mock(ExamPermitRepository.class);
        ExamRepository exams = Mockito.mock(ExamRepository.class);
        SchoolService schoolService = Mockito.mock(SchoolService.class);
        AuditLogRepository auditLogs = Mockito.mock(AuditLogRepository.class);
        UserRepository users = Mockito.mock(UserRepository.class);
        ExamPermitService service = new ExamPermitService(repository, exams, schoolService, auditLogs, users);

        Exam exam = new Exam();
        exam.setCourseId(5L);
        exam.setTitle("Mid Semester Examination");
        Mockito.when(exams.findById(12L)).thenReturn(Optional.of(exam));
        Mockito.when(schoolService.studentEnrolledInCourse(21L, 5L)).thenReturn(true);

        ExamPermit permit = new ExamPermit();
        permit.setId(7L);
        permit.setStudentId(21L);
        permit.setExamId(12L);
        permit.setStatus(ExamPermitStatus.PENDING);
        permit.setRequestedAt(LocalDateTime.now());

        User adminUser = new User();
        adminUser.setId(99L);
        adminUser.setUsername("admin");
        Mockito.when(users.findByUsernameNormalized("admin")).thenReturn(Optional.of(adminUser));
        Mockito.when(repository.save(Mockito.any(ExamPermit.class))).thenReturn(permit);
        Mockito.when(repository.findByExamIdAndStudentId(12L, 21L)).thenReturn(Optional.of(permit));
        Mockito.when(repository.findById(7L)).thenReturn(Optional.of(permit));
        Mockito.when(repository.findByStudentId(21L)).thenReturn(List.of(permit));

        ExamPermit created = service.requestPermit(21L, 12L);
        assertEquals(ExamPermitStatus.PENDING, created.getStatus());

        ExamPermit approved = service.approvePermit(7L, "admin");
        assertEquals(ExamPermitStatus.APPROVED, approved.getStatus());
        assertTrue(service.hasApprovedPermit(21L, 12L));
    }
}
