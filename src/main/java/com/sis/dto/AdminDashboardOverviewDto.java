package com.sis.dto;

import java.math.BigDecimal;

public class AdminDashboardOverviewDto {
    private Long totalStudents;
    private Long totalTeachers;
    private Long totalUsers;
    private Long totalCourses;
    private Long totalFees;
    private Long paidFeesCount;
    private Long unpaidFeesCount;
    private BigDecimal totalRevenue;

    public AdminDashboardOverviewDto() {
    }

    public AdminDashboardOverviewDto(Long totalStudents, Long totalTeachers, Long totalUsers,
                                    Long totalCourses, Long totalFees, Long paidFeesCount,
                                    Long unpaidFeesCount, BigDecimal totalRevenue) {
        this.totalStudents = totalStudents;
        this.totalTeachers = totalTeachers;
        this.totalUsers = totalUsers;
        this.totalCourses = totalCourses;
        this.totalFees = totalFees;
        this.paidFeesCount = paidFeesCount;
        this.unpaidFeesCount = unpaidFeesCount;
        this.totalRevenue = totalRevenue;
    }

    public Long getTotalStudents() { return totalStudents; }
    public void setTotalStudents(Long totalStudents) { this.totalStudents = totalStudents; }

    public Long getTotalTeachers() { return totalTeachers; }
    public void setTotalTeachers(Long totalTeachers) { this.totalTeachers = totalTeachers; }

    public Long getTotalUsers() { return totalUsers; }
    public void setTotalUsers(Long totalUsers) { this.totalUsers = totalUsers; }

    public Long getTotalCourses() { return totalCourses; }
    public void setTotalCourses(Long totalCourses) { this.totalCourses = totalCourses; }

    public Long getTotalFees() { return totalFees; }
    public void setTotalFees(Long totalFees) { this.totalFees = totalFees; }

    public Long getPaidFeesCount() { return paidFeesCount; }
    public void setPaidFeesCount(Long paidFeesCount) { this.paidFeesCount = paidFeesCount; }

    public Long getUnpaidFeesCount() { return unpaidFeesCount; }
    public void setUnpaidFeesCount(Long unpaidFeesCount) { this.unpaidFeesCount = unpaidFeesCount; }

    public BigDecimal getTotalRevenue() { return totalRevenue; }
    public void setTotalRevenue(BigDecimal totalRevenue) { this.totalRevenue = totalRevenue; }
}
