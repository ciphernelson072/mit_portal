package com.sis.dto;

import java.util.List;

public class TeacherResultPublicationStatusDto {
    private Long studentId;
    private String studentName;
    private List<String> courseCodes;
    private String status;
    private String message;

    public TeacherResultPublicationStatusDto() {
    }

    public TeacherResultPublicationStatusDto(Long studentId, String studentName, List<String> courseCodes,
                                             String status, String message) {
        this.studentId = studentId;
        this.studentName = studentName;
        this.courseCodes = courseCodes;
        this.status = status;
        this.message = message;
    }

    public Long getStudentId() {
        return studentId;
    }

    public String getStudentName() {
        return studentName;
    }

    public List<String> getCourseCodes() {
        return courseCodes;
    }

    public String getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }
}
