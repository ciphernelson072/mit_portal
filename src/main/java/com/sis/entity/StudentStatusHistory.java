package com.sis.entity;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "student_status_history")
public class StudentStatusHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_program_id")
    private StudentProgram studentProgram;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StudentLifecycleStatus status;

    @Column(length = 2000)
    private String reason;

    @Column(name = "changed_by", nullable = false, length = 255)
    private String changedBy;

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Student getStudent() { return student; }
    public void setStudent(Student student) { this.student = student; }
    public StudentProgram getStudentProgram() { return studentProgram; }
    public void setStudentProgram(StudentProgram studentProgram) { this.studentProgram = studentProgram; }
    public StudentLifecycleStatus getStatus() { return status; }
    public void setStatus(StudentLifecycleStatus status) { this.status = status; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getChangedBy() { return changedBy; }
    public void setChangedBy(String changedBy) { this.changedBy = changedBy; }
    public LocalDateTime getChangedAt() { return changedAt; }
    public void setChangedAt(LocalDateTime changedAt) { this.changedAt = changedAt; }
}