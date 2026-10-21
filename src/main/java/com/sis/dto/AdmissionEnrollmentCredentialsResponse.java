package com.sis.dto;

public class AdmissionEnrollmentCredentialsResponse {
    private final Long id;
    private final Long studentId;
    private final Long programId;
    private final Long academicYearId;
    private final String status;
    private final String username;
    private String temporaryPassword;
    private boolean emailSent;
    private String emailDeliveryMessage;

    public AdmissionEnrollmentCredentialsResponse(Long id, Long studentId, Long programId,
            Long academicYearId, String status, String username, String temporaryPassword) {
        this.id = id;
        this.studentId = studentId;
        this.programId = programId;
        this.academicYearId = academicYearId;
        this.status = status;
        this.username = username;
        this.temporaryPassword = temporaryPassword;
    }

    public Long getId() {
        return id;
    }

    public Long getStudentId() {
        return studentId;
    }

    public Long getProgramId() {
        return programId;
    }

    public Long getAcademicYearId() {
        return academicYearId;
    }

    public String getStatus() {
        return status;
    }

    public String getUsername() {
        return username;
    }

    public String getTemporaryPassword() {
        return temporaryPassword;
    }

    public void setTemporaryPassword(String temporaryPassword) {
        this.temporaryPassword = temporaryPassword;
    }

    public boolean isEmailSent() {
        return emailSent;
    }

    public String getEmailDeliveryMessage() {
        return emailDeliveryMessage;
    }

    public void recordEmailDelivery(boolean sent, String message) {
        this.emailSent = sent;
        this.emailDeliveryMessage = message;
        if (sent) {
            this.temporaryPassword = null;
        }
    }
}
