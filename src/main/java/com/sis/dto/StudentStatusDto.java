package com.sis.dto;

import java.math.BigDecimal;
import java.util.List;
/**
 * Combined "at a glance" dashboard payload for a logged-in student:
 * current program and lifecycle status, outstanding balance, next fee deadline,
 * recent grades and aggregate attendance.
 * Replaces the previous handmade {@code Map<String,Object>} that was being
 * assembled with business logic sitting directly inside StudentController.
 */
public class StudentStatusDto {
    private BigDecimal balance;
    private String nextDueDate;
    private String studentProgramName;
    private String studentLifecycleStatus;
    private List<GradeDto> grades;
    private String resultPublicationStatus;
    private Integer creditsEarned;
    private String academicStandingStatus;
    private List<String> missingResultCourses;
    private long attendanceCount;
    private long presentAttendanceCount;

    public StudentStatusDto() {}

    public StudentStatusDto(BigDecimal balance, String nextDueDate, String studentProgramName,
                            String studentLifecycleStatus, List<GradeDto> grades, String resultPublicationStatus,
                            Integer creditsEarned, String academicStandingStatus, List<String> missingResultCourses,
                            long attendanceCount, long presentAttendanceCount) {
        this.balance = balance;
        this.nextDueDate = nextDueDate;
        this.studentProgramName = studentProgramName;
        this.studentLifecycleStatus = studentLifecycleStatus;
        this.grades = grades;
        this.resultPublicationStatus = resultPublicationStatus;
        this.creditsEarned = creditsEarned;
        this.academicStandingStatus = academicStandingStatus;
        this.missingResultCourses = missingResultCourses;
        this.attendanceCount = attendanceCount;
        this.presentAttendanceCount = presentAttendanceCount;
    }

    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }
    public String getNextDueDate() { return nextDueDate; }
    public void setNextDueDate(String nextDueDate) { this.nextDueDate = nextDueDate; }
    public String getStudentProgramName() { return studentProgramName; }
    public void setStudentProgramName(String studentProgramName) { this.studentProgramName = studentProgramName; }
    public String getStudentLifecycleStatus() { return studentLifecycleStatus; }
    public void setStudentLifecycleStatus(String studentLifecycleStatus) { this.studentLifecycleStatus = studentLifecycleStatus; }
    public List<GradeDto> getGrades() { return grades; }
    public void setGrades(List<GradeDto> grades) { this.grades = grades; }
    public String getResultPublicationStatus() { return resultPublicationStatus; }
    public void setResultPublicationStatus(String resultPublicationStatus) { this.resultPublicationStatus = resultPublicationStatus; }
    public Integer getCreditsEarned() { return creditsEarned; }
    public void setCreditsEarned(Integer creditsEarned) { this.creditsEarned = creditsEarned; }
    public String getAcademicStandingStatus() { return academicStandingStatus; }
    public void setAcademicStandingStatus(String academicStandingStatus) { this.academicStandingStatus = academicStandingStatus; }
    public List<String> getMissingResultCourses() { return missingResultCourses; }
    public void setMissingResultCourses(List<String> missingResultCourses) { this.missingResultCourses = missingResultCourses; }
    public long getAttendanceCount() { return attendanceCount; }
    public void setAttendanceCount(long attendanceCount) { this.attendanceCount = attendanceCount; }
    public long getPresentAttendanceCount() { return presentAttendanceCount; }
    public void setPresentAttendanceCount(long presentAttendanceCount) { this.presentAttendanceCount = presentAttendanceCount; }
}
