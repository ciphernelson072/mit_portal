package com.sis.controller;

import com.sis.dto.AuthRequest;
import com.sis.dto.AuthResponse;
import com.sis.entity.Role;
import com.sis.service.JwtService;
import com.sis.service.SchoolService;
import com.sis.service.AuthenticationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.*;

/**
 * Authentication Controller - Handles login, registration, password reset, email verification
 * Public endpoints for authentication-related operations
 */
@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final SchoolService schoolService;

    @Autowired
    private AuthenticationService authenticationService;

    public AuthController(AuthenticationManager authenticationManager, JwtService jwtService,
                          SchoolService schoolService) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.schoolService = schoolService;
    }

    /**
     * User login endpoint
     * @param request contains username and password
     * @return JWT token and role if successful
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody AuthRequest request) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
            );
            String role = authentication.getAuthorities().stream().findFirst().map(Object::toString).orElse("STUDENT");
            String token = jwtService.generateToken(request.getUsername(), role);
            return ResponseEntity.ok(new AuthResponse(token, role));
        } catch (AuthenticationException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    /**
     * User registration endpoint
     * @param request contains username and password
     * @param role user role (ADMIN, TEACHER, STUDENT)
     * @return success or error response
     */
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody AuthRequest request, @RequestParam Role role) {
        if (!schoolService.createUser(request.getUsername(), request.getPassword(), role).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Username already exists");
        }
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /**
     * Request password reset
     * Sends reset email to user
     * @param email user email address
     * @return success response
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<String> requestPasswordReset(@RequestParam String email) {
        authenticationService.requestPasswordReset(email);
        return ResponseEntity.ok("Password reset email sent to: " + email);
    }

    /**
     * Validate password reset token
     * @param token reset token
     * @return true if valid
     */
    @GetMapping("/validate-reset-token")
    public ResponseEntity<Boolean> validateResetToken(@RequestParam String token) {
        boolean isValid = authenticationService.validatePasswordResetToken(token);
        return ResponseEntity.ok(isValid);
    }

    /**
     * Reset password using token
     * @param token reset token
     * @param newPassword new password
     * @return success response
     */
    @PostMapping("/reset-password")
    public ResponseEntity<String> resetPassword(@RequestParam String token, @RequestParam String newPassword) {
        authenticationService.resetPassword(token, newPassword);
        return ResponseEntity.ok("Password reset successful. Please log in with your new password.");
    }

    /**
     * Verify email address
     * @param token verification token
     * @return success response
     */
    @PostMapping("/verify-email")
    public ResponseEntity<String> verifyEmail(@RequestParam String token) {
        authenticationService.confirmEmail(token);
        return ResponseEntity.ok("Email verified successfully");
    }

    /**
     * Validate email verification token
     * @param token verification token
     * @return true if valid
     */
    @GetMapping("/validate-email-token")
    public ResponseEntity<Boolean> validateEmailToken(@RequestParam String token) {
        boolean isValid = authenticationService.validateEmailVerificationToken(token);
        return ResponseEntity.ok(isValid);
    }

    /**
     * Resend password reset email
     * @param email user email
     * @return success response
     */
    @PostMapping("/resend-password-reset")
    public ResponseEntity<String> resendPasswordReset(@RequestParam String email) {
        authenticationService.resendPasswordResetEmail(email);
        return ResponseEntity.ok("Password reset email resent");
    }

    /**
     * Resend email verification
     * @param email user email
     * @return success response
     */
    @PostMapping("/resend-email-verification")
    public ResponseEntity<String> resendEmailVerification(@RequestParam String email) {
        authenticationService.resendEmailVerification(email);
        return ResponseEntity.ok("Email verification resent");
    }
}
