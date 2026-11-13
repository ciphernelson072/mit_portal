package com.sis.entity;

import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "accreditation_records")
public class AccreditationRecord {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "program_id", nullable = false) private Program program;
    @Column(nullable = false, length = 200) private String accreditingBody;
    @Column(name = "certificate_reference", length = 100) private String certificateReference;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private AccreditationStatus status = AccreditationStatus.DRAFT;
    @Column(name = "valid_from") private LocalDate validFrom;
    @Column(name = "valid_until") private LocalDate validUntil;
    @Column(name = "evidence_path", length = 500) private String evidencePath;
    @Column(name = "reviewed_by") private String reviewedBy;
    @Column(name = "reviewed_at") private LocalDateTime reviewedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Program getProgram() { return program; }
    public void setProgram(Program program) { this.program = program; }
    public String getAccreditingBody() { return accreditingBody; }
    public void setAccreditingBody(String accreditingBody) { this.accreditingBody = accreditingBody; }
    public String getCertificateReference() { return certificateReference; }
    public void setCertificateReference(String certificateReference) { this.certificateReference = certificateReference; }
    public AccreditationStatus getStatus() { return status; }
    public void setStatus(AccreditationStatus status) { this.status = status; }
    public LocalDate getValidFrom() { return validFrom; }
    public void setValidFrom(LocalDate validFrom) { this.validFrom = validFrom; }
    public LocalDate getValidUntil() { return validUntil; }
    public void setValidUntil(LocalDate validUntil) { this.validUntil = validUntil; }
    public String getEvidencePath() { return evidencePath; }
    public void setEvidencePath(String evidencePath) { this.evidencePath = evidencePath; }
    public String getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(String reviewedBy) { this.reviewedBy = reviewedBy; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }
}