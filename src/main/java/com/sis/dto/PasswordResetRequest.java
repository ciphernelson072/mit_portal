package com.sis.dto;

/**
 * Password Reset Request DTO - Contains password reset information
 */
public class PasswordResetRequest {

    /**
     * Reset token received in email
     */
    private String token;

    /**
     * New password
     */
    private String newPassword;

    /**
     * Password confirmation
     */
    private String confirmPassword;

    // Constructors
    public PasswordResetRequest() {}

    public PasswordResetRequest(String token, String newPassword, String confirmPassword) {
        this.token = token;
        this.newPassword = newPassword;
        this.confirmPassword = confirmPassword;
    }

    // Getters and Setters
    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }

    public String getConfirmPassword() {
        return confirmPassword;
    }

    public void setConfirmPassword(String confirmPassword) {
        this.confirmPassword = confirmPassword;
    }

    /**
     * Check if both password fields match
     * @return true if passwords match
     */
    public boolean isPasswordMatch() {
        return newPassword != null && newPassword.equals(confirmPassword);
    }

    @Override
    public String toString() {
        return "PasswordResetRequest{" +
                "token='" + token.substring(0, Math.min(10, token.length())) + "...'" +
                ", newPassword='***'" +
                '}';
    }
}
