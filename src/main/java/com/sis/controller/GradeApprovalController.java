package com.sis.controller;

import com.sis.dto.AdminGradeDto;
import com.sis.dto.GradeDto;
import com.sis.dto.RejectionRequest;
import com.sis.entity.Course;
import com.sis.entity.Grade;
import com.sis.entity.GradeStatus;
import com.sis.entity.Student;
import com.sis.exception.ResourceNotFoundException;
import com.sis.repository.GradeRepository;
import com.sis.service.GradeApprovalService;
import com.sis.service.SchoolService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class GradeApprovalController {

    private final GradeApprovalService gradeApprovalService;
    private final SchoolService schoolService;
    private final GradeRepository gradeRepository;

    public GradeApprovalController(GradeApprovalService gradeApprovalService, SchoolService schoolService,
                                   GradeRepository gradeRepository) {
        this.gradeApprovalService = gradeApprovalService;
        this.schoolService = schoolService;
        this.gradeRepository = gradeRepository;
    }

    @PostMapping("/teacher/grades/{gradeId}/submit")
    @PreAuthorize("hasAuthority('TEACHER')")
    public ResponseEntity<GradeDto> submitForApproval(@PathVariable Long gradeId) {
        Grade grade = schoolService.getGradeById(gradeId)
                .orElseThrow(() -> new ResourceNotFoundException("Grade not found: " + gradeId));
        String teacher = SecurityContextHolder.getContext().getAuthentication().getName();
        if (!schoolService.teacherOwnsCourse(teacher, grade.getCourse())) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN).build();
        }
        Grade saved = gradeApprovalService.submitForApproval(grade, teacher);
        return ResponseEntity.ok(schoolService.toGradeDto(saved));
    }

    @PostMapping("/admin/grades/{gradeId}/approve")
    @PreAuthorize("@permissionSecurity.canReviewGradeApprovals()")
    public ResponseEntity<GradeDto> approve(@PathVariable Long gradeId) {
        String admin = SecurityContextHolder.getContext().getAuthentication().getName();
        Grade approved = gradeApprovalService.approve(gradeId, admin);
        return ResponseEntity.ok(schoolService.toGradeDto(approved));
    }

    @PostMapping("/admin/grades/{gradeId}/review")
    @PreAuthorize("@permissionSecurity.canReviewGradeApprovals()")
    public ResponseEntity<Void> acknowledgeAssessmentReview(@PathVariable Long gradeId) {
        String admin = SecurityContextHolder.getContext().getAuthentication().getName();
        gradeApprovalService.acknowledgeAssessmentReview(gradeId, admin);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/admin/grades/{gradeId}/reject")
    @PreAuthorize("@permissionSecurity.canReviewGradeApprovals()")
    public ResponseEntity<GradeDto> reject(@PathVariable Long gradeId, @Valid @RequestBody RejectionRequest body) {
        String admin = SecurityContextHolder.getContext().getAuthentication().getName();
        Grade rejected = gradeApprovalService.reject(gradeId, admin, body.getReason());
        return ResponseEntity.ok(schoolService.toGradeDto(rejected));
    }

    @GetMapping("/admin/grades/pending")
    @PreAuthorize("@permissionSecurity.canReviewGradeApprovals()")
    public ResponseEntity<List<AdminGradeDto>> pendingGrades() {
        List<AdminGradeDto> pending = gradeRepository.findPendingWithStudentUserAndCourse(GradeStatus.PENDING).stream()
                .map(this::toAdminGradeDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(pending);
    }

    private AdminGradeDto toAdminGradeDto(Grade grade) {
        Student student = grade.getStudent();
        Course course = grade.getCourse();
        return new AdminGradeDto(
                grade.getId(),
                student != null ? student.getId() : null,
                student != null ? student.getFullName() : null,
                student != null && student.getUser() != null ? student.getUser().getUsername() : null,
                course != null ? course.getId() : null,
                course != null ? course.getName() : null,
                grade.getGradeValue(),
                grade.getGpaScore(),
                grade.getRemarks(),
                grade.getStatus() != null ? grade.getStatus().name() : "PENDING",
                grade.getSubmittedBy(),
                grade.getApprovedBy(),
                grade.getRejectedBy(),
                grade.getRejectionReason(),
                grade.getTotalMark(),
                grade.getRetakeRequired(),
                grade.getGradingPolicyVersion()
        );
    }
}
