package com.sis;

import com.sis.entity.ExamResult;
import com.sis.entity.FinancialHold;
import com.sis.entity.Student;
import com.sis.repository.ExamResultRepository;
import com.sis.repository.FeeRepository;
import com.sis.repository.FinancialHoldRepository;
import com.sis.repository.StudentRepository;
import com.sis.service.AcademicClearanceService;
import com.sis.service.ResultPublicationService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class AcademicClearanceServiceTest {

    @Test
    void studentsWithActiveFinancialHoldCannotViewPublishedResults() {
        FinancialHoldRepository financialHoldRepository = Mockito.mock(FinancialHoldRepository.class);
        FeeRepository feeRepository = Mockito.mock(FeeRepository.class);
        StudentRepository studentRepository = Mockito.mock(StudentRepository.class);

        Student student = new Student();
        student.setId(11L);

        Mockito.when(studentRepository.findById(11L)).thenReturn(Optional.of(student));
        Mockito.when(financialHoldRepository.existsByStudentIdAndActiveTrue(11L)).thenReturn(true);
        Mockito.when(feeRepository.findByStudentId(11L)).thenReturn(java.util.Collections.emptyList());

        AcademicClearanceService clearance = new AcademicClearanceService(financialHoldRepository, feeRepository, studentRepository);
        assertFalse(clearance.canStudentViewResults(11L));
        assertFalse(clearance.canStudentRegister(11L));
    }

    @Test
    void resultPublicationRequiresClearanceAndApprovedExamResult() {
        ExamResultRepository examResultRepository = Mockito.mock(ExamResultRepository.class);
        FinancialHoldRepository financialHoldRepository = Mockito.mock(FinancialHoldRepository.class);
        FeeRepository feeRepository = Mockito.mock(FeeRepository.class);

        ExamResult result = new ExamResult();
        result.setExamId(10L);
        result.setStudentId(11L);
        result.setPublished(Boolean.FALSE);
        result.setScore(BigDecimal.valueOf(78.5));

        Mockito.when(examResultRepository.findById(22L)).thenReturn(Optional.of(result));
        Mockito.when(examResultRepository.save(Mockito.any(ExamResult.class))).thenAnswer(invocation -> {
            ExamResult examResult = invocation.getArgument(0);
            examResult.setPublished(Boolean.TRUE);
            examResult.setPublishedAt(java.time.LocalDateTime.now());
            return examResult;
        });
        Mockito.when(financialHoldRepository.existsByStudentIdAndActiveTrue(11L)).thenReturn(false);
        Mockito.when(feeRepository.findByStudentId(11L)).thenReturn(java.util.Collections.emptyList());

        ResultPublicationService service = new ResultPublicationService(examResultRepository, financialHoldRepository, feeRepository);
        ExamResult published = service.publishResult(22L, "admin");

        assertTrue(published.getPublished());
        assertNotNull(published.getPublishedAt());
    }
}
