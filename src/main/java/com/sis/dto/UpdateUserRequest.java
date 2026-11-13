package com.sis.dto;

import javax.validation.constraints.Email;
import javax.validation.constraints.Size;
import com.sis.entity.Role;

public class UpdateUserRequest {

    @Email(message = "Email must be valid")
    private String email;

    private String phone;

    // Optional - only updated if provided (min length enforced only when present,
    // since blank/null here legitimately means "leave the password unchanged")
    @Size(min = 6, message = "Password must be at least 6 characters")
    private String password;

    private Role role;

    public UpdateUserRequest() {
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

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }
}
