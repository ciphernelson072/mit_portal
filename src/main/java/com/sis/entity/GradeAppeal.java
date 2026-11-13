package com.sis.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.LocalDateTime;

@Entity
@Table(name = "grade_appeals", indexes = {
        @Index(name = "idx_grade_appeals_status_submitted", columnList = "status, submitted_at"),
        @Index(name = "idx_grade_appeals_grade_status", columnList = "grade_id, status")
})
public class GradeAppeal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(name = "row_version", nullable = false)
    private Long rowVersion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "grade_id", nullable = false)
    private Grade grade;

    @Column(nullable = false, length = 2000)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private GradeAppealStatus status = GradeAppealStatus.OPEN;

    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt;

    @Column(name = "consultation_requested_by")
    private String consultationRequestedBy;

    @Column(name = "consultation_requested_at")
    private LocalDateTime consultationRequestedAt;

    @Column(name = "teacher_response", length = 2000)
    private String teacherResponse;

    @Column(name = "teacher_responded_at")
    private LocalDateTime teacherRespondedAt;

    @Column(name = "teacher_consulted_by")
    private String teacherConsultedBy;

    @Column(name = "teacher_consulted_at")
    private LocalDateTime teacherConsultedAt;

    @Column(name = "decision_by")
    private String decisionBy;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @Column(name = "decision_note", length = 2000)
    private String decisionNote;

    protected GradeAppeal() {
    }

    public GradeAppeal(Grade grade, String reason, LocalDateTime submittedAt) {
        this.grade = grade;
        this.reason = reason;
        this.submittedAt = submittedAt;
    }

    public Long getId() { return id; }
    public Grade getGrade() { return grade; }
    public String getReason() { return reason; }
    public GradeAppealStatus getStatus() { return status; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public String getConsultationRequestedBy() { return consultationRequestedBy; }
    public LocalDateTime getConsultationRequestedAt() { return consultationRequestedAt; }
    public String getTeacherResponse() { return teacherResponse; }
    public LocalDateTime getTeacherRespondedAt() { return teacherRespondedAt; }
    public String getTeacherConsultedBy() { return teacherConsultedBy; }
    public LocalDateTime getTeacherConsultedAt() { return teacherConsultedAt; }
    public String getDecisionBy() { return decisionBy; }
    public LocalDateTime getDecidedAt() { return decidedAt; }
    public String getDecisionNote() { return decisionNote; }

    public void requestTeacherConsultation(String username, LocalDateTime requestedAt) {
        this.consultationRequestedBy = username;
        this.consultationRequestedAt = requestedAt;
        this.status = GradeAppealStatus.TEACHER_REVIEW;
    }

    public void recordTeacherResponse(String response, String username, LocalDateTime respondedAt) {
        this.teacherResponse = response;
        this.teacherRespondedAt = respondedAt;
        this.teacherConsultedBy = username;
        this.teacherConsultedAt = respondedAt;
        this.status = GradeAppealStatus.READY_FOR_DECISION;
    }

    public void confirmTeacherConsultation(String username, LocalDateTime consultedAt) {
        this.teacherConsultedBy = username;
        this.teacherConsultedAt = consultedAt;
        this.status = GradeAppealStatus.READY_FOR_DECISION;
    }

    public void decide(GradeAppealStatus decision, String username, String note, LocalDateTime decidedAt) {
        this.status = decision;
        this.decisionBy = username;
        this.decisionNote = note;
        this.decidedAt = decidedAt;
    }
}
