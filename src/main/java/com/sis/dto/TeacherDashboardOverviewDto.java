package com.sis.dto;

public class TeacherDashboardOverviewDto {
    private Long assignedCourseCount;
    private Long enrolledStudentCount;
    private Long pendingGradeCount;
    private Long upcomingAssignmentCount;
    private Long pendingAttendanceCount;

    public TeacherDashboardOverviewDto() {
    }

    public TeacherDashboardOverviewDto(Long assignedCourseCount, Long enrolledStudentCount,
                                      Long pendingGradeCount, Long upcomingAssignmentCount,
                                      Long pendingAttendanceCount) {
        this.assignedCourseCount = assignedCourseCount;
        this.enrolledStudentCount = enrolledStudentCount;
        this.pendingGradeCount = pendingGradeCount;
        this.upcomingAssignmentCount = upcomingAssignmentCount;
        this.pendingAttendanceCount = pendingAttendanceCount;
    }

    public Long getAssignedCourseCount() { return assignedCourseCount; }
    public void setAssignedCourseCount(Long assignedCourseCount) { this.assignedCourseCount = assignedCourseCount; }

    public Long getEnrolledStudentCount() { return enrolledStudentCount; }
    public void setEnrolledStudentCount(Long enrolledStudentCount) { this.enrolledStudentCount = enrolledStudentCount; }

    public Long getPendingGradeCount() { return pendingGradeCount; }
    public void setPendingGradeCount(Long pendingGradeCount) { this.pendingGradeCount = pendingGradeCount; }

    public Long getUpcomingAssignmentCount() { return upcomingAssignmentCount; }
    public void setUpcomingAssignmentCount(Long upcomingAssignmentCount) { this.upcomingAssignmentCount = upcomingAssignmentCount; }

    public Long getPendingAttendanceCount() { return pendingAttendanceCount; }
    public void setPendingAttendanceCount(Long pendingAttendanceCount) { this.pendingAttendanceCount = pendingAttendanceCount; }
}
