package com.sis.service;

import com.sis.entity.*;
import com.sis.repository.CurriculumCourseRepository;
import com.sis.repository.GradeRepository;
import com.sis.repository.ProgramRepository;
import com.sis.repository.StudentProgramRepository;
import com.sis.repository.StudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class AcademicRecordsService {
    private final StudentRepository students;
    private final StudentProgramRepository studentPrograms;
    private final CurriculumCourseRepository curriculum;
    private final GradeRepository grades;
    private final AcademicClearanceService clearance;
    private final ProgramRepository programs;

    public AcademicRecordsService(StudentRepository students, StudentProgramRepository studentPrograms,
                                  CurriculumCourseRepository curriculum, GradeRepository grades,
                                  AcademicClearanceService clearance, ProgramRepository programs) {
        this.students = students;
        this.studentPrograms = studentPrograms;
        this.curriculum = curriculum;
        this.grades = grades;
        this.clearance = clearance;
        this.programs = programs;
    }

    public Map<String, Object> transcript(Long studentId) {
        Student student = students.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Student not found"));
        List<Grade> approved = grades.findByStudentIdAndStatus(studentId, GradeStatus.APPROVED);
        List<Map<String, Object>> entries = approved.stream().map(this::gradeEntry).toList();
        return map("studentId", student.getId(), "studentName", student.getFullName(),
                "username", student.getUser().getUsername(), "cgpa",
                approved.isEmpty() ? null : GradeCalculationService.calculateApprovedCgpa(approved),
                "courseCount", entries.size(), "grades", entries);
    }

    public Map<String, Object> degreeAudit(Long studentId) {
        Student student = students.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Student not found"));
        StudentProgram program = studentPrograms.findByStudentIdOrderByStartDateDesc(studentId).stream()
                .filter(item -> item.getStatus() == StudentLifecycleStatus.ACTIVE)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Student has no active program"));
        return degreeAudit(studentId, program);
    }

    public Map<String, Object> degreeAudit(Long studentId, Long programId, Long academicYearId) {
        students.findById(studentId).orElseThrow(() -> new IllegalArgumentException("Student not found"));
        StudentProgram enrollment = studentPrograms
                .findByStudentIdAndProgramIdAndAcademicYearId(studentId, programId, academicYearId)
                .filter(item -> item.getStatus() == StudentLifecycleStatus.ACTIVE)
                .orElseThrow(() -> new IllegalStateException("Student has no active enrollment for this programme and year"));
        return degreeAudit(studentId, enrollment);
    }

    private Map<String, Object> degreeAudit(Long studentId, StudentProgram program) {
        List<Grade> approved = grades.findByStudentIdAndStatus(studentId, GradeStatus.APPROVED);
        List<Map<String, Object>> missing = new ArrayList<>();
        int requiredCount = 0;
        int completedCount = 0;
        for (CurriculumCourse item : curriculum.findByProgramIdOrderBySemesterNumberAscCourseCourseCodeAsc(program.getProgram().getId())) {
            if (!Boolean.TRUE.equals(item.getRequired())) continue;
            requiredCount++;
            boolean completed = approved.stream().anyMatch(grade -> grade.getCourse().getId().equals(item.getCourse().getId())
                    && grade.getGpaScore() != null
                    && grade.getGpaScore().compareTo(UgandanTertiaryGradingPolicy.PASS_GRADE_POINT) >= 0);
            if (completed) completedCount++;
            else missing.add(map("courseId", item.getCourse().getId(), "courseCode", item.getCourse().getCourseCode(),
                    "courseName", item.getCourse().getName(), "semesterNumber", item.getSemesterNumber()));
        }
        boolean directoryActive = programs.existsActiveInDirectory(program.getProgram().getId());
        boolean academicRequirementsMet = requiredCount > 0 && requiredCount == completedCount;
        boolean financialHold = clearance.hasActiveFinancialHold(studentId);
        boolean outstandingFees = clearance.hasOutstandingFees(studentId);
        boolean financialClearanceGranted = !financialHold && !outstandingFees;
        return map("studentId", studentId, "programId", program.getProgram().getId(),
                "programName", program.getProgram().getName(), "academicYearId", program.getAcademicYear().getId(),
                "academicYearName", program.getAcademicYear().getName(), "requiredCourses", requiredCount,
                "completedRequiredCourses", completedCount, "missingRequiredCourses", missing,
                "academicRequirementsMet", academicRequirementsMet,
                "directoryActive", directoryActive,
                "activeFinancialHold", financialHold,
                "outstandingFees", outstandingFees,
                "financialClearanceGranted", financialClearanceGranted,
                "eligibleForGraduation", academicRequirementsMet && directoryActive && financialClearanceGranted);
    }

    private Map<String, Object> gradeEntry(Grade grade) {
        return map("courseId", grade.getCourse().getId(), "courseCode", grade.getCourse().getCourseCode(),
                "courseName", grade.getCourse().getName(), "credits", grade.getCourse().getCredits(),
                "grade", grade.getGradeValue(), "gpaScore", grade.getGpaScore(), "approvedAt", grade.getApprovedAt());
    }

    private Map<String, Object> map(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) result.put((String) values[index], values[index + 1]);
        return result;
    }
}