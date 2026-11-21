package com.sis.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

public class UpdateStudentRequest {

    @NotBlank(message = "Full name is required")
    private String fullName;

    private String className;

    @Email(message = "Email must be valid")
    private String email;

    private String phone;

    private String address;
    private String dateOfBirth;
    private String gender;
    private String alternativePhone;
    private String city;
    private String country;

    public UpdateStudentRequest() {
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

    public String getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(String dateOfBirth) {
        if (dateOfBirth != null && !dateOfBirth.isBlank()) {
            if (!dateOfBirth.trim().matches("\\d{4}-\\d{2}-\\d{2}")) {
                throw new IllegalArgumentException(
                        "Date of birth must be a valid date in YYYY-MM-DD format.");
            }
            try {
                LocalDate parsed = LocalDate.parse(dateOfBirth.trim());
                if (parsed.isBefore(LocalDate.of(1900, 1, 1))
                        || parsed.isAfter(LocalDate.now())) {
                    throw new IllegalArgumentException(
                            "Date of birth must be between 1900-01-01 and today.");
                }
            } catch (DateTimeParseException exception) {
                throw new IllegalArgumentException(
                        "Date of birth must be a valid date in YYYY-MM-DD format.");
            }
        }
        this.dateOfBirth = dateOfBirth == null ? null : dateOfBirth.trim();
    }
    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }
    public String getAlternativePhone() { return alternativePhone; }
    public void setAlternativePhone(String alternativePhone) { this.alternativePhone = alternativePhone; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }
}
