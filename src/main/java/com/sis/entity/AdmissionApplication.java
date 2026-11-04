package com.sis.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Locale;

@Entity
@Table(name = "admission_applications", uniqueConstraints = {
        @UniqueConstraint(name = "uk_admission_application_number", columnNames = "application_number"),
        @UniqueConstraint(name = "uk_admission_email_normalized_year",
                columnNames = {"email_normalized", "academic_year_id"})
})
public class AdmissionApplication {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "application_number", nullable = false, length = 30)
    private String applicationNumber;

    @Column(name = "applicant_name", nullable = false, length = 200)
    private String applicantName;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(name = "email_normalized", nullable = false, length = 255)
    private String normalizedEmail;

    @Column(length = 30)
    private String phone;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "program_id", nullable = false)
    private Program program;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "academic_year_id", nullable = false)
    private AcademicYear academicYear;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AdmissionApplicationStatus status = AdmissionApplicationStatus.SUBMITTED;

    @Column(length = 2000)
    private String reviewerNotes;

    @Column(nullable = false)
    private LocalDateTime submittedAt = LocalDateTime.now();

    @Column
    private LocalDateTime decidedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getApplicationNumber() { return applicationNumber; }
    public void setApplicationNumber(String applicationNumber) { this.applicationNumber = applicationNumber; }
    public String getApplicantName() { return applicantName; }
    public void setApplicantName(String applicantName) { this.applicantName = applicantName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getNormalizedEmail() { return normalizedEmail; }
    public void setNormalizedEmail(String normalizedEmail) { this.normalizedEmail = normalizedEmail; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public Program getProgram() { return program; }
    public void setProgram(Program program) { this.program = program; }
    public AcademicYear getAcademicYear() { return academicYear; }
    public void setAcademicYear(AcademicYear academicYear) { this.academicYear = academicYear; }
    public AdmissionApplicationStatus getStatus() { return status; }
    public void setStatus(AdmissionApplicationStatus status) { this.status = status; }
    public String getReviewerNotes() { return reviewerNotes; }
    public void setReviewerNotes(String reviewerNotes) { this.reviewerNotes = reviewerNotes; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }
    public LocalDateTime getDecidedAt() { return decidedAt; }
    public void setDecidedAt(LocalDateTime decidedAt) { this.decidedAt = decidedAt; }

    @PrePersist
    @PreUpdate
    private void normalizeEmail() {
        if (email != null) normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
    }
}