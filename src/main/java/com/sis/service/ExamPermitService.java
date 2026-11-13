package com.sis.service;

import com.sis.entity.AuditLog;
import com.sis.entity.ExamPermit;
import com.sis.entity.ExamPermitStatus;
import com.sis.entity.Exam;
import com.sis.repository.AuditLogRepository;
import com.sis.repository.ExamPermitRepository;
import com.sis.repository.ExamRepository;
import com.sis.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
public class ExamPermitService {

    private final ExamPermitRepository repository;
    private final ExamRepository exams;
    private final SchoolService schoolService;
    private final AuditLogRepository auditLogs;
    private final UserRepository users;

    public ExamPermitService(ExamPermitRepository repository, ExamRepository exams, SchoolService schoolService,
                            AuditLogRepository auditLogs, UserRepository users) {
        this.repository = repository;
        this.exams = exams;
        this.schoolService = schoolService;
        this.auditLogs = auditLogs;
        this.users = users;
    }

    public ExamPermit requestPermit(Long studentId, Long examId) {
        if (studentId == null || examId == null) {
            throw new IllegalArgumentException("Student and exam are required");
        }
        Exam exam = exams.findById(examId)
                .orElseThrow(() -> new IllegalArgumentException("Exam not found"));
        if (!schoolService.studentEnrolledInCourse(studentId, exam.getCourseId())) {
            throw new IllegalArgumentException("Student is not enrolled in the exam course");
        }

        return repository.findByExamIdAndStudentId(examId, studentId).map(existing -> {
            if (existing.getStatus() == ExamPermitStatus.APPROVED) {
                throw new IllegalStateException("Exam permit already approved");
            }
            if (existing.getStatus() == ExamPermitStatus.PENDING) {
                return existing;
            }
            existing.setStatus(ExamPermitStatus.PENDING);
            existing.setRequestedAt(LocalDateTime.now());
            existing.setApprovedAt(null);
            existing.setApprovedBy(null);
            existing.setRejectedAt(null);
            existing.setRejectedBy(null);
            existing.setReason(null);
            return repository.save(existing);
        }).orElseGet(() -> {
            ExamPermit permit = new ExamPermit();
            permit.setStudentId(studentId);
            permit.setExamId(examId);
            permit.setStatus(ExamPermitStatus.PENDING);
            permit.setRequestedAt(LocalDateTime.now());
            return repository.save(permit);
        });
    }

    @Transactional(readOnly = true)
    public List<ExamPermit> listForStudent(Long studentId) {
        return repository.findByStudentId(studentId);
    }

    @Transactional(readOnly = true)
    public List<ExamPermit> listPending() {
        return repository.findByStatus(ExamPermitStatus.PENDING);
    }

    public ExamPermit approvePermit(Long permitId, String approvedBy) {
        ExamPermit permit = repository.findById(permitId)
                .orElseThrow(() -> new IllegalArgumentException("Exam permit not found"));
        if (permit.getStatus() != ExamPermitStatus.PENDING) {
            throw new IllegalStateException("Only pending permits can be approved");
        }
        permit.setStatus(ExamPermitStatus.APPROVED);
        permit.setApprovedAt(LocalDateTime.now());
        permit.setApprovedBy(approvedBy);
        permit.setRejectedAt(null);
        permit.setRejectedBy(null);
        ExamPermit saved = repository.save(permit);
        Long userId = users.findByUsernameNormalized(approvedBy)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + approvedBy))
                .getId();
        auditLogs.save(new AuditLog(userId, "EXAM_PERMIT_APPROVED",
                "Permit #" + saved.getId() + " approved for student #" + saved.getStudentId() + " exam #" + saved.getExamId()));
        return saved;
    }

    public ExamPermit rejectPermit(Long permitId, String rejectedBy, String reason) {
        ExamPermit permit = repository.findById(permitId)
                .orElseThrow(() -> new IllegalArgumentException("Exam permit not found"));
        if (permit.getStatus() != ExamPermitStatus.PENDING) {
            throw new IllegalStateException("Only pending permits can be rejected");
        }
        permit.setStatus(ExamPermitStatus.REJECTED);
        permit.setRejectedAt(LocalDateTime.now());
        permit.setRejectedBy(rejectedBy);
        permit.setReason(reason);
        permit.setApprovedAt(null);
        permit.setApprovedBy(null);
        ExamPermit saved = repository.save(permit);
        Long userId = users.findByUsernameNormalized(rejectedBy)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + rejectedBy))
                .getId();
        auditLogs.save(new AuditLog(userId, "EXAM_PERMIT_REJECTED",
                "Permit #" + saved.getId() + " rejected for student #" + saved.getStudentId() + " exam #" + saved.getExamId() + ": " + reason));
        return saved;
    }

    @Transactional(readOnly = true)
    public boolean hasApprovedPermit(Long studentId, Long examId) {
        return repository.findByExamIdAndStudentId(examId, studentId)
                .map(permit -> permit.getStatus() == ExamPermitStatus.APPROVED)
                .orElse(false);
    }
}
