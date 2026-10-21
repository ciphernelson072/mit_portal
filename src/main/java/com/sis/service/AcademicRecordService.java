package com.sis.service;

import com.sis.dto.AttendanceDto;
import com.sis.dto.FeeDto;
import com.sis.dto.GradeDto;
import com.sis.dto.StudentStatusDto;
import com.sis.entity.Attendance;
import com.sis.entity.Course;
import com.sis.entity.Grade;
import com.sis.entity.GradeStatus;
import com.sis.entity.Student;
import com.sis.repository.AttendanceRepository;
import com.sis.repository.GradeRepository;
import com.sis.repository.StudentRepository;
import com.sis.repository.StudentProgramRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Grades, attendance, and the combined student-status dashboard payload.
 * <p>
 * Split out of the former monolithic SchoolService (which mixed users, students,
 * teachers, courses, grades, attendance, announcements, fees, materials, and
 * messages in one class) into its own focused, independently testable service -
 * this one owns exactly the "academic record" concern.
 */
@Service
@Transactional
public class AcademicRecordService {

    private final GradeRepository gradeRepository;
    private final AttendanceRepository attendanceRepository;
    private final FeeService feeService;
    private final StudentRepository studentRepository;
    private final StudentProgramRepository studentPrograms;

    public AcademicRecordService(GradeRepository gradeRepository,
                                  AttendanceRepository attendanceRepository,
                                  FeeService feeService) {
        this(gradeRepository, attendanceRepository, feeService, null, null);
    }

    public AcademicRecordService(GradeRepository gradeRepository, AttendanceRepository attendanceRepository,
                                  FeeService feeService, StudentRepository studentRepository) {
        this(gradeRepository, attendanceRepository, feeService, studentRepository, null);
    }

    @Autowired
    public AcademicRecordService(GradeRepository gradeRepository, AttendanceRepository attendanceRepository,
                                  FeeService feeService, StudentRepository studentRepository,
                                  StudentProgramRepository studentPrograms) {
        this.gradeRepository = gradeRepository;
        this.attendanceRepository = attendanceRepository;
        this.feeService = feeService;
        this.studentRepository = studentRepository;
        this.studentPrograms = studentPrograms;
    }

    public Grade saveGrade(Student student, Course course, String gradeValue, String remarks) {
        return saveGrade(student, course, gradeValue, remarks, null);
    }

    public Grade saveGrade(Student student, Course course, String gradeValue, String remarks, String submittedBy) {
        Grade grade = new Grade(student, course, gradeValue, remarks);
        grade.setGpaScore(GradeCalculationService.gradeToGpa(gradeValue));
        grade.setStatus(GradeStatus.PENDING);
        grade.setSubmittedBy(submittedBy);
        grade.setSubmittedAt(LocalDateTime.now());
        Grade saved = gradeRepository.save(grade);
        refreshCgpa(student.getId());
        return reloadGradeForResponse(saved);
    }

    public Grade updateGradeScore(Grade grade, BigDecimal gpaScore) {
        grade.setGpaScore(gpaScore);
        grade.setStatus(GradeStatus.PENDING);
        Grade saved = gradeRepository.save(grade);
        refreshCgpa(saved.getStudent().getId());
        return reloadGradeForResponse(saved);
    }

    public Grade updateGrade(Grade grade, String gradeValue, String remarks, BigDecimal gpaScore) {
        grade.setGradeValue(gradeValue);
        grade.setRemarks(remarks);
        grade.setGpaScore(GradeCalculationService.gradeToGpa(gradeValue));
        if (gpaScore != null) grade.setGpaScore(gpaScore);
        grade.setStatus(GradeStatus.PENDING);
        grade.setSubmittedAt(LocalDateTime.now());
        Grade saved = gradeRepository.save(grade);
        refreshCgpa(saved.getStudent().getId());
        return reloadGradeForResponse(saved);
    }

    private Grade reloadGradeForResponse(Grade grade) {
        return gradeRepository.findByIdWithStudentUserAndCourse(grade.getId())
                .orElseThrow(() -> new IllegalStateException("Saved grade could not be reloaded."));
    }

    public void deleteGrade(Grade grade) {
        Long studentId = grade.getStudent() != null ? grade.getStudent().getId() : null;
        gradeRepository.delete(grade);
        if (studentId != null) {
            refreshCgpa(studentId);
        }
    }

    public List<Grade> getGradesForStudent(Long studentId) {
        return studentId == null ? Collections.emptyList()
                : gradeRepository.findByStudentIdAndStatusOrderByApprovedAtDescIdDesc(studentId, GradeStatus.APPROVED);
    }

    public List<Attendance> getAttendanceForStudent(Long studentId) {
        return studentId == null ? Collections.emptyList() : attendanceRepository.findByStudentIdWithCourse(studentId);
    }

    public GradeDto toGradeDto(Grade grade) {
        GradeDto dto = new GradeDto();
        dto.setId(grade.getId());
        dto.setCourseId(grade.getCourse() != null ? grade.getCourse().getId() : null);
        dto.setCourseCode(grade.getCourse() != null ? grade.getCourse().getCourseCode() : null);
        dto.setCourseName(grade.getCourse() != null ? grade.getCourse().getName() : "Unknown Course");
        dto.setGradeValue(grade.getGradeValue());
        dto.setGpaScore(grade.getGpaScore());
        dto.setRemarks(grade.getRemarks());
        dto.setCredits(grade.getCourse() != null ? grade.getCourse().getCredits() : null);
        dto.setSemester(grade.getCourse() != null ? grade.getCourse().getSemester() : null);
        dto.setStatus(grade.getStatus() != null ? grade.getStatus().name() : "PENDING");
        dto.setSubmittedBy(grade.getSubmittedBy());
        dto.setApprovedBy(grade.getApprovedBy());
        dto.setRejectionReason(grade.getRejectionReason());
        return dto;
    }

    public List<GradeDto> getGradeDtosForStudent(Long studentId) {
        return getGradesForStudent(studentId).stream().map(this::toGradeDto).collect(Collectors.toList());
    }

    private void refreshCgpa(Long studentId) {
        if (studentId == null || studentRepository == null) return;
        Optional<Student> studentOpt = studentRepository.findById(studentId);
        if (studentOpt.isEmpty()) return;

        List<Grade> grades = gradeRepository.findByStudentId(studentId);
        if (grades.isEmpty()) {
            Student student = studentOpt.get();
            student.setCgpa(BigDecimal.ZERO);
            studentRepository.save(student);
            return;
        }

        BigDecimal cgpa = GradeCalculationService.calculateApprovedCgpa(grades);

        Student student = studentOpt.get();
        student.setCgpa(cgpa);
        studentRepository.save(student);
    }

    public AttendanceDto toAttendanceDto(Attendance attendance) {
        AttendanceDto dto = new AttendanceDto();
        dto.setId(attendance.getId());
        dto.setStudentId(attendance.getStudent() != null ? attendance.getStudent().getId() : null);
        dto.setStudentName(attendance.getStudent() != null ? attendance.getStudent().getFullName() : null);
        dto.setCourseId(attendance.getCourse() != null ? attendance.getCourse().getId() : null);
        dto.setCourseName(attendance.getCourse() != null ? attendance.getCourse().getName() : "Unknown Course");
        dto.setDate(attendance.getDate());
        dto.setPresent(attendance.isPresent());
        dto.setRemarks(attendance.getRemarks());
        dto.setClassStartTime(attendance.getClassStartTime());
        dto.setClassEndTime(attendance.getClassEndTime());
        dto.setLate(attendance.isLate());
        return dto;
    }

    public List<AttendanceDto> getAttendanceDtosForStudent(Long studentId) {
        return getAttendanceForStudent(studentId).stream().map(this::toAttendanceDto).collect(Collectors.toList());
    }

    /**
     * Combined dashboard payload for a logged-in student: outstanding fee balance
     * (sum of unpaid fees), the earliest upcoming due date among them, recent grades
     * and attendance.
     */
    public StudentStatusDto getStudentStatus(Long studentId) {
        List<FeeDto> fees = feeService.getFeesForStudent(studentId);

        BigDecimal balance = fees.stream()
                .filter(fee -> !fee.isPaid())
                .map(FeeDto::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        String nextDueDate = fees.stream()
                .filter(fee -> !fee.isPaid() && fee.getDueDate() != null)
                .map(FeeDto::getDueDate)
                .filter(dueDate -> !dueDate.isBefore(LocalDate.now()))
                .min(Comparator.naturalOrder())
                .map(LocalDate::toString)
                .orElse("None");

        StudentStatusDto dto = new StudentStatusDto();
        dto.setBalance(balance);
        dto.setNextDueDate(nextDueDate);
        if (studentPrograms != null) {
            studentPrograms.findByStudentIdOrderByStartDateDesc(studentId).stream()
                    .findFirst()
                    .ifPresent(program -> {
                        if (program.getProgram() != null) {
                            dto.setStudentProgramName(program.getProgram().getName());
                        }
                        if (program.getStatus() != null) {
                            dto.setStudentLifecycleStatus(program.getStatus().name());
                        }
                    });
        }
        if (studentId == null) {
            dto.setGrades(Collections.emptyList());
        } else {
            dto.setGrades(gradeRepository.findTop5ByStudentIdAndStatusOrderByApprovedAtDescIdDesc(
                            studentId, GradeStatus.APPROVED)
                    .stream().map(this::toGradeDto).collect(Collectors.toList()));
            dto.setAttendanceCount(attendanceRepository.countByStudentId(studentId));
            dto.setPresentAttendanceCount(attendanceRepository.countByStudentIdAndPresentTrue(studentId));
        }
        return dto;
    }
}
