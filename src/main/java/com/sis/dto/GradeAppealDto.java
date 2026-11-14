package com.sis.dto;

import java.time.LocalDateTime;

public class GradeAppealDto {
    private final Long id;
    private final Long gradeId;
    private final String studentName;
    private final String registrationNumber;
    private final String courseCode;
    private final String courseName;
    private final String reason;
    private final String status;
    private final LocalDateTime submittedAt;
    private final LocalDateTime consultationRequestedAt;
    private final String teacherResponse;
    private final LocalDateTime teacherRespondedAt;
    private final LocalDateTime teacherConsultedAt;
    private final String decisionBy;
    private final LocalDateTime decidedAt;
    private final String decisionNote;

    public GradeAppealDto(Long id, Long gradeId, String studentName, String registrationNumber,
                          String courseCode, String courseName, String reason, String status,
                          LocalDateTime submittedAt, LocalDateTime consultationRequestedAt,
                          String teacherResponse, LocalDateTime teacherRespondedAt,
                          LocalDateTime teacherConsultedAt, String decisionBy,
                          LocalDateTime decidedAt, String decisionNote) {
        this.id = id;
        this.gradeId = gradeId;
        this.studentName = studentName;
        this.registrationNumber = registrationNumber;
        this.courseCode = courseCode;
        this.courseName = courseName;
        this.reason = reason;
        this.status = status;
        this.submittedAt = submittedAt;
        this.consultationRequestedAt = consultationRequestedAt;
        this.teacherResponse = teacherResponse;
        this.teacherRespondedAt = teacherRespondedAt;
        this.teacherConsultedAt = teacherConsultedAt;
        this.decisionBy = decisionBy;
        this.decidedAt = decidedAt;
        this.decisionNote = decisionNote;
    }

    public Long getId() { return id; }
    public Long getGradeId() { return gradeId; }
    public String getStudentName() { return studentName; }
    public String getRegistrationNumber() { return registrationNumber; }
    public String getCourseCode() { return courseCode; }
    public String getCourseName() { return courseName; }
    public String getReason() { return reason; }
    public String getStatus() { return status; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public LocalDateTime getConsultationRequestedAt() { return consultationRequestedAt; }
    public String getTeacherResponse() { return teacherResponse; }
    public LocalDateTime getTeacherRespondedAt() { return teacherRespondedAt; }
    public LocalDateTime getTeacherConsultedAt() { return teacherConsultedAt; }
    public String getDecisionBy() { return decisionBy; }
    public LocalDateTime getDecidedAt() { return decidedAt; }
    public String getDecisionNote() { return decisionNote; }
}
