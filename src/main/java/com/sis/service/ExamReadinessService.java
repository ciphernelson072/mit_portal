package com.sis.service;

import com.sis.dto.ExamReadinessDto;
import com.sis.dto.ExamReadinessItemDto;
import com.sis.entity.Attendance;
import com.sis.entity.Exam;
import com.sis.entity.RegistrationStatus;
import com.sis.repository.AttendanceRepository;
import com.sis.repository.ExamRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
public class ExamReadinessService {
    private static final BigDecimal ATTENDANCE_WARNING_THRESHOLD = BigDecimal.valueOf(75);

    private final ExamRepository exams;
    private final AttendanceRepository attendance;
    private final FinancialHoldService financialHolds;
    private final SchoolService schoolService;

    public ExamReadinessService(
            ExamRepository exams,
            AttendanceRepository attendance,
            FinancialHoldService financialHolds,
            SchoolService schoolService) {
        this.exams = exams;
        this.attendance = attendance;
        this.financialHolds = financialHolds;
        this.schoolService = schoolService;
    }

    @Transactional(readOnly = true)
    public ExamReadinessDto assess(Long studentId, Long examId) {
        if (studentId == null || examId == null) {
            throw new IllegalArgumentException("Student and exam are required.");
        }
        Exam exam = exams.findById(examId)
                .orElseThrow(() -> new IllegalArgumentException("Exam not found."));
        return assess(studentId, exam);
    }

    @Transactional(readOnly = true)
    public List<ExamReadinessItemDto> listForStudent(Long studentId) {
        return exams.findForStudentByEnrolledCourses(studentId, RegistrationStatus.REGISTERED).stream()
                .map(exam -> new ExamReadinessItemDto(
                        exam.getId(), exam.getTitle(), assess(studentId, exam)))
                .toList();
    }

    private ExamReadinessDto assess(Long studentId, Exam exam) {
        boolean enrolled = schoolService.studentEnrolledInCourse(studentId, exam.getCourseId());
        List<Attendance> records = attendance.findByStudentIdAndCourseId(studentId, exam.getCourseId());
        long sessions = records.size();
        long present = records.stream().filter(Attendance::isPresent).count();
        boolean hasRecords = sessions > 0;
        BigDecimal percentage = hasRecords
                ? BigDecimal.valueOf(present).multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(sessions), 1, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(1);
        boolean attendanceWarning = hasRecords && percentage.compareTo(ATTENDANCE_WARNING_THRESHOLD) < 0;
        boolean activeFinancialHold = financialHolds.hasActiveHold(studentId);

        List<String> warnings = new ArrayList<>();
        if (!enrolled) {
            warnings.add("Student is not currently enrolled in the exam course.");
        }
        if (!hasRecords) {
            warnings.add("Attendance has not yet been recorded for this course.");
        } else if (attendanceWarning) {
            warnings.add("Recorded course attendance is below 75%; staff should review before approving the permit.");
        }
        if (activeFinancialHold) {
            warnings.add("An active financial hold requires finance review.");
        }
        return new ExamReadinessDto(enrolled, hasRecords, sessions, percentage,
                attendanceWarning, activeFinancialHold, warnings);
    }
}
