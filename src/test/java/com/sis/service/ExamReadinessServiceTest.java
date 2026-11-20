package com.sis.service;

import com.sis.entity.Attendance;
import com.sis.entity.Exam;
import com.sis.repository.AttendanceRepository;
import com.sis.repository.ExamRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExamReadinessServiceTest {
    private final ExamRepository exams = mock(ExamRepository.class);
    private final AttendanceRepository attendance = mock(AttendanceRepository.class);
    private final FinancialHoldService financialHolds = mock(FinancialHoldService.class);
    private final SchoolService schoolService = mock(SchoolService.class);
    private final ExamReadinessService service =
            new ExamReadinessService(exams, attendance, financialHolds, schoolService);

    @Test
    void reportsAttendanceAndFinancialWarningsWithoutBlockingPermitReadiness() {
        Exam exam = exam();
        Attendance present = new Attendance();
        present.setPresent(true);
        Attendance absent = new Attendance();
        absent.setPresent(false);
        when(exams.findById(12L)).thenReturn(Optional.of(exam));
        when(schoolService.studentEnrolledInCourse(21L, 5L)).thenReturn(true);
        when(attendance.findByStudentIdAndCourseId(21L, 5L)).thenReturn(List.of(present, absent));
        when(financialHolds.hasActiveHold(21L)).thenReturn(true);

        var result = service.assess(21L, 12L);

        assertThat(result.enrolled()).isTrue();
        assertThat(result.attendancePercentage()).isEqualByComparingTo("50.0");
        assertThat(result.belowAttendanceThreshold()).isTrue();
        assertThat(result.activeFinancialHold()).isTrue();
        assertThat(result.warnings()).hasSize(2);
    }

    @Test
    void doesNotFlagAttendanceThresholdWhenNoAttendanceHasBeenRecorded() {
        when(exams.findById(12L)).thenReturn(Optional.of(exam()));
        when(schoolService.studentEnrolledInCourse(21L, 5L)).thenReturn(true);
        when(attendance.findByStudentIdAndCourseId(21L, 5L)).thenReturn(List.of());
        when(financialHolds.hasActiveHold(21L)).thenReturn(false);

        var result = service.assess(21L, 12L);

        assertThat(result.hasAttendanceRecords()).isFalse();
        assertThat(result.belowAttendanceThreshold()).isFalse();
        assertThat(result.warnings()).containsExactly("Attendance has not yet been recorded for this course.");
    }

    private Exam exam() {
        Exam exam = new Exam();
        exam.setCourseId(5L);
        exam.setTitle("Final Examination");
        return exam;
    }
}
