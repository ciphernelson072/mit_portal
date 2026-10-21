package com.sis.service;

import com.sis.entity.Grade;
import com.sis.entity.GradeStatus;
import com.sis.entity.NotificationCategory;
import com.sis.repository.GradeRepository;
import com.sis.repository.AuditLogRepository;
import com.sis.entity.AuditLog;
import com.sis.repository.UserRepository;
import com.sis.repository.StudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@Transactional
public class GradeApprovalService {

    private final GradeRepository gradeRepository;
    private final StudentRepository studentRepository;
    private final StudentService studentService;
    private final AuditLogRepository auditLogs;
    private final UserRepository users;
    private final NotificationService notifications;

    public GradeApprovalService(GradeRepository gradeRepository,
                                 StudentRepository studentRepository,
                                 StudentService studentService,
                                 AuditLogRepository auditLogs,
                                 UserRepository users,
                                 NotificationService notifications) {
        this.gradeRepository = gradeRepository;
        this.studentRepository = studentRepository;
        this.studentService = studentService;
        this.auditLogs = auditLogs;
        this.users = users;
        this.notifications = notifications;
    }

    public Grade submitForApproval(Grade grade, String submittedBy) {
        grade.setSubmittedBy(submittedBy);
        grade.setSubmittedAt(LocalDateTime.now());
        grade.setStatus(GradeStatus.PENDING);
        grade.setApprovedBy(null);
        grade.setRejectedBy(null);
        grade.setRejectionReason(null);
        grade.setApprovedAt(null);
        grade.setRejectedAt(null);
        Grade saved = gradeRepository.save(grade);
        audit(submittedBy, "GRADE_SUBMITTED_FOR_APPROVAL", "Grade #" + saved.getId() + " submitted for approval");
        return reloadGradeForResponse(saved);
    }

    public Grade approve(Long gradeId, String approvedBy) {
        Grade grade = gradeRepository.findByIdForUpdate(gradeId)
                .orElseThrow(() -> new IllegalArgumentException("Grade not found: " + gradeId));
        if (grade.getStatus() != GradeStatus.PENDING) {
            throw new IllegalStateException("Only pending grades can be approved. Current status: " + grade.getStatus());
        }
        grade.setStatus(GradeStatus.APPROVED);
        grade.setApprovedBy(approvedBy);
        grade.setApprovedAt(LocalDateTime.now());
        grade.setRejectedBy(null);
        grade.setRejectionReason(null);
        grade.setRejectedAt(null);
        Grade saved = gradeRepository.save(grade);
        studentService.calculateCGPA(saved.getStudent().getId());
        audit(approvedBy, "GRADE_APPROVED", "Grade #" + saved.getId() + " approved");
        notifications.create(
                saved.getStudent().getUser().getId(),
                "Grade available",
                "Your grade for " + saved.getCourse().getName() + " is now available.",
                NotificationCategory.GRADE);
        return reloadGradeForResponse(saved);
    }

    public Grade reject(Long gradeId, String rejectedBy, String reason) {
        Grade grade = gradeRepository.findByIdForUpdate(gradeId)
                .orElseThrow(() -> new IllegalArgumentException("Grade not found: " + gradeId));
        if (grade.getStatus() != GradeStatus.PENDING) {
            throw new IllegalStateException("Only pending grades can be rejected. Current status: " + grade.getStatus());
        }
        grade.setStatus(GradeStatus.REJECTED);
        grade.setRejectedBy(rejectedBy);
        grade.setRejectedAt(LocalDateTime.now());
        grade.setRejectionReason(reason);
        grade.setApprovedBy(null);
        grade.setApprovedAt(null);
        Grade saved = gradeRepository.save(grade);
        studentService.calculateCGPA(saved.getStudent().getId());
        audit(rejectedBy, "GRADE_REJECTED", "Grade #" + saved.getId() + " rejected");
        return reloadGradeForResponse(saved);
    }

    private Grade reloadGradeForResponse(Grade grade) {
        return gradeRepository.findByIdWithStudentUserAndCourse(grade.getId())
                .orElseThrow(() -> new IllegalStateException("Saved grade could not be reloaded."));
    }

    private void audit(String username, String action, String details) {
        Long userId = users.findByUsernameNormalized(username)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + username))
                .getId();
        auditLogs.save(new AuditLog(userId, action, details));
    }
}
