package com.sis.repository;

public interface AttendanceRiskCandidate {
    Long getTermId();
    Long getUserId();
    Long getStudentId();
    Long getCourseId();
    String getCourseCode();
    String getCourseName();
    Long getPresentSessions();
    Long getTotalSessions();
}
