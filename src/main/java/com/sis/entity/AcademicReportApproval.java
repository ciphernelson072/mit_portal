package com.sis.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "academic_report_approvals")
public class AcademicReportApproval {

    @Id
    @Column(name = "student_id")
    private Long studentId;

    @Column(name = "approved_by", nullable = false, length = 255)
    private String approvedBy;

    @Column(name = "approved_at", nullable = false)
    private LocalDateTime approvedAt;

    protected AcademicReportApproval() {
    }

    public AcademicReportApproval(Long studentId, String approvedBy, LocalDateTime approvedAt) {
        this.studentId = studentId;
        this.approvedBy = approvedBy;
        this.approvedAt = approvedAt;
    }

    public Long getStudentId() {
        return studentId;
    }

    public String getApprovedBy() {
        return approvedBy;
    }

    public LocalDateTime getApprovedAt() {
        return approvedAt;
    }
}
