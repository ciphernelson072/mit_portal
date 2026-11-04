package com.sis.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public class CreateAccreditationRequest {
    @NotNull private Long programId;
    @NotBlank private String accreditingBody;
    private String certificateReference;
    private LocalDate validFrom;
    private LocalDate validUntil;
    private String evidencePath;
    public Long getProgramId() { return programId; }
    public void setProgramId(Long programId) { this.programId = programId; }
    public String getAccreditingBody() { return accreditingBody; }
    public void setAccreditingBody(String accreditingBody) { this.accreditingBody = accreditingBody; }
    public String getCertificateReference() { return certificateReference; }
    public void setCertificateReference(String certificateReference) { this.certificateReference = certificateReference; }
    public LocalDate getValidFrom() { return validFrom; }
    public void setValidFrom(LocalDate validFrom) { this.validFrom = validFrom; }
    public LocalDate getValidUntil() { return validUntil; }
    public void setValidUntil(LocalDate validUntil) { this.validUntil = validUntil; }
    public String getEvidencePath() { return evidencePath; }
    public void setEvidencePath(String evidencePath) { this.evidencePath = evidencePath; }
}