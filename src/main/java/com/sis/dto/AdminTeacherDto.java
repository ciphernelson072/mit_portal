package com.sis.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

public class AdminTeacherDto {
    private Long id;
    private String fullName;
    private String username;
    private String email;
    private String phone;
    private String qualification;
    private Integer yearsOfExperience;
    private String department;
    private Long departmentId;
    private String departmentName;
    private String profileImagePath;
    private boolean active;
    private Boolean emailSent;
    private String emailDeliveryMessage;

    public AdminTeacherDto() {
    }

    public AdminTeacherDto(Long id, String fullName, String username, String email, String phone,
                            String qualification, Integer yearsOfExperience, String department,
                            String profileImagePath, boolean active) {
        this.id = id;
        this.fullName = fullName;
        this.username = username;
        this.email = email;
        this.phone = phone;
        this.qualification = qualification;
        this.yearsOfExperience = yearsOfExperience;
        this.department = department;
        this.profileImagePath = profileImagePath;
        this.active = active;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getQualification() {
        return qualification;
    }

    public void setQualification(String qualification) {
        this.qualification = qualification;
    }

    public Integer getYearsOfExperience() {
        return yearsOfExperience;
    }

    public void setYearsOfExperience(Integer yearsOfExperience) {
        this.yearsOfExperience = yearsOfExperience;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public Long getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(Long departmentId) {
        this.departmentId = departmentId;
    }

    public String getDepartmentName() {
        return departmentName;
    }

    public void setDepartmentName(String departmentName) {
        this.departmentName = departmentName;
    }

    public String getProfileImagePath() {
        return profileImagePath;
    }

    public void setProfileImagePath(String profileImagePath) {
        this.profileImagePath = profileImagePath;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Boolean getEmailSent() {
        return emailSent;
    }

    public void setEmailSent(Boolean emailSent) {
        this.emailSent = emailSent;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getEmailDeliveryMessage() {
        return emailDeliveryMessage;
    }

    public void setEmailDeliveryMessage(String emailDeliveryMessage) {
        this.emailDeliveryMessage = emailDeliveryMessage;
    }
}
