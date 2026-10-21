package com.sis.dto;

import java.math.BigDecimal;

public class AdminStudentDto {
    private Long id;
    private String fullName;
    private String className;
    private String username;
    private String email;
    private String phone;
    private String address;
    private String registrationNumber;
    private BigDecimal cgpa;
    private String profileImagePath;
    private boolean active;

    public AdminStudentDto() {
    }

    public AdminStudentDto(Long id, String fullName, String className, String username,
                            String email, String phone, String address, BigDecimal cgpa,
                            String profileImagePath, boolean active) {
        this.id = id;
        this.fullName = fullName;
        this.className = className;
        this.username = username;
        this.email = email;
        this.phone = phone;
        this.address = address;
        this.cgpa = cgpa;
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

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
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

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getRegistrationNumber() { return registrationNumber; }
    public void setRegistrationNumber(String registrationNumber) { this.registrationNumber = registrationNumber; }

    public BigDecimal getCgpa() {
        return cgpa;
    }

    public void setCgpa(BigDecimal cgpa) {
        this.cgpa = cgpa;
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
}
