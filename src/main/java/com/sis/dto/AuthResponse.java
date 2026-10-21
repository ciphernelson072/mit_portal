package com.sis.dto;

/**
 * Login response. Deliberately does NOT include the raw JWT - the access token now
 * lives only in an HttpOnly cookie, invisible to JavaScript. Putting it back in this
 * response body would undermine the whole point (an XSS bug could just read it from
 * the fetch() response instead of localStorage - no better off). role/username are
 * not sensitive and are safe for the frontend to keep in localStorage purely for
 * UI purposes (which dashboard to show, etc.) - they grant nothing on their own.
 * The password-change flag only directs provisioned users to the restricted
 * password-change screen; authorization is enforced independently by the API.
 */
public class AuthResponse {
    private String role;
    private String username;
    private boolean passwordChangeRequired;

    public AuthResponse(String role, String username) {
        this(role, username, false);
    }

    public AuthResponse(String role, String username, boolean passwordChangeRequired) {
        this.role = role;
        this.username = username;
        this.passwordChangeRequired = passwordChangeRequired;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public boolean isPasswordChangeRequired() {
        return passwordChangeRequired;
    }

    public void setPasswordChangeRequired(boolean passwordChangeRequired) {
        this.passwordChangeRequired = passwordChangeRequired;
    }
}
