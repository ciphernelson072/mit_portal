package com.sis.service;

import com.sis.dto.CreateFinancialHoldRequest;
import com.sis.entity.AuditLog;
import com.sis.entity.FinancialHold;
import com.sis.repository.AuditLogRepository;
import com.sis.repository.FinancialHoldRepository;
import com.sis.repository.StudentRepository;
import com.sis.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
public class FinancialHoldService {
    private final FinancialHoldRepository holds;
    private final StudentRepository students;
    private final AuditLogRepository auditLogs;
    private final UserRepository users;

    public FinancialHoldService(FinancialHoldRepository holds, StudentRepository students,
                                AuditLogRepository auditLogs, UserRepository users) {
        this.holds = holds;
        this.students = students;
        this.auditLogs = auditLogs;
        this.users = users;
    }

    public FinancialHold create(CreateFinancialHoldRequest request, String createdBy) {
        FinancialHold hold = new FinancialHold();
        hold.setStudent(students.findByIdWithUser(request.getStudentId())
                .orElseThrow(() -> new IllegalArgumentException("Student not found")));
        hold.setReason(request.getReason());
        hold.setCreatedBy(createdBy);
        FinancialHold saved = holds.save(hold);
        audit(createdBy, "FINANCIAL_HOLD_CREATED", "Hold #" + saved.getId() + " created for student #"
            + saved.getStudent().getId() + ": " + saved.getReason());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<FinancialHold> activeForStudent(Long studentId) {
        return holds.findByStudentIdAndActiveTrueOrderByCreatedAtDesc(studentId);
    }

    public void release(Long id, String releasedBy) {
        FinancialHold hold = holds.findById(id).orElseThrow(() -> new IllegalArgumentException("Financial hold not found"));
        if (!Boolean.TRUE.equals(hold.getActive())) throw new IllegalStateException("Financial hold is already released");
        hold.setActive(false);
        hold.setReleasedAt(LocalDateTime.now());
        FinancialHold saved = holds.save(hold);
        audit(releasedBy, "FINANCIAL_HOLD_RELEASED", "Hold #" + saved.getId() + " released for student #"
                + saved.getStudent().getId());
    }

    @Transactional(readOnly = true)
    public boolean hasActiveHold(Long studentId) { return holds.existsByStudentIdAndActiveTrue(studentId); }

    private void audit(String username, String action, String details) {
        Long userId = users.findByUsernameNormalized(username)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + username))
                .getId();
        auditLogs.save(new AuditLog(userId, action, details));
    }
}