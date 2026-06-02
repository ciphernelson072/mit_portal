package com.sis.dto;

/**
 * DTO for Admin Dashboard Statistics
 * Returns aggregated statistics about users, students, and teachers
 */
public class AdminStatisticsDto {

    private Long totalStudents;
    private Long totalTeachers;
    private Long totalUsers;
    private Long deletedUsersCount;

    // Default constructor
    public AdminStatisticsDto() {
    }

    // Full constructor
    public AdminStatisticsDto(Long totalStudents, Long totalTeachers, Long totalUsers, Long deletedUsersCount) {
        this.totalStudents = totalStudents;
        this.totalTeachers = totalTeachers;
        this.totalUsers = totalUsers;
        this.deletedUsersCount = deletedUsersCount;
    }

    // Getters and Setters

    /**
     * Get total active students
     * @return count of students
     */
    public Long getTotalStudents() {
        return totalStudents;
    }

    /**
     * Set total students
     * @param totalStudents number of students
     */
    public void setTotalStudents(Long totalStudents) {
        this.totalStudents = totalStudents;
    }

    /**
     * Get total active teachers
     * @return count of teachers
     */
    public Long getTotalTeachers() {
        return totalTeachers;
    }

    /**
     * Set total teachers
     * @param totalTeachers number of teachers
     */
    public void setTotalTeachers(Long totalTeachers) {
        this.totalTeachers = totalTeachers;
    }

    /**
     * Get total users (including all roles)
     * @return count of users
     */
    public Long getTotalUsers() {
        return totalUsers;
    }

    /**
     * Set total users
     * @param totalUsers number of users
     */
    public void setTotalUsers(Long totalUsers) {
        this.totalUsers = totalUsers;
    }

    /**
     * Get count of soft-deleted users
     * @return count of deleted users
     */
    public Long getDeletedUsersCount() {
        return deletedUsersCount;
    }

    /**
     * Set deleted users count
     * @param deletedUsersCount number of deleted users
     */
    public void setDeletedUsersCount(Long deletedUsersCount) {
        this.deletedUsersCount = deletedUsersCount;
    }
}
