package com.sis.dto;

import java.math.BigDecimal;

/**
 * DTO for Student CGPA information
 * Used in admin and student endpoints to return CGPA data
 */
public class StudentCGPADto {

    private Long id;
    private String fullName;
    private BigDecimal cgpa;

    // Default constructor
    public StudentCGPADto() {
    }

    // Full constructor
    public StudentCGPADto(Long id, String fullName, BigDecimal cgpa) {
        this.id = id;
        this.fullName = fullName;
        this.cgpa = cgpa;
    }

    // Getters and Setters

    /**
     * Get student ID
     * @return student ID
     */
    public Long getId() {
        return id;
    }

    /**
     * Set student ID
     * @param id student ID
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * Get full name
     * @return full name
     */
    public String getFullName() {
        return fullName;
    }

    /**
     * Set full name
     * @param fullName student's full name
     */
    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    /**
     * Get CGPA
     * @return CGPA value
     */
    public BigDecimal getCgpa() {
        return cgpa;
    }

    /**
     * Set CGPA
     * @param cgpa cumulative GPA
     */
    public void setCgpa(BigDecimal cgpa) {
        this.cgpa = cgpa;
    }
}
