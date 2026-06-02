package com.sis.exception;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Standard Error Response DTO
 * Used for consistent error response format across the API
 */
public class ErrorResponse {

    // HTTP status code
    private int statusCode;

    // Error message description
    private String message;

    // Timestamp when error occurred
    private LocalDateTime timestamp;

    // Request path that caused the error
    private String path;

    // Field-level validation errors
    private Map<String, String> validationErrors;

    // Default constructor
    public ErrorResponse() {
    }

    // Constructor with basic fields
    public ErrorResponse(int statusCode, String message, LocalDateTime timestamp, String path) {
        this.statusCode = statusCode;
        this.message = message;
        this.timestamp = timestamp;
        this.path = path;
    }

    // Getters and Setters

    /**
     * Get HTTP status code
     * @return status code
     */
    public int getStatusCode() {
        return statusCode;
    }

    /**
     * Set HTTP status code
     * @param statusCode HTTP status code
     */
    public void setStatusCode(int statusCode) {
        this.statusCode = statusCode;
    }

    /**
     * Get error message
     * @return error message
     */
    public String getMessage() {
        return message;
    }

    /**
     * Set error message
     * @param message error message text
     */
    public void setMessage(String message) {
        this.message = message;
    }

    /**
     * Get timestamp when error occurred
     * @return timestamp
     */
    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    /**
     * Set timestamp
     * @param timestamp when error occurred
     */
    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    /**
     * Get request path that caused error
     * @return request path
     */
    public String getPath() {
        return path;
    }

    /**
     * Set request path
     * @param path request path
     */
    public void setPath(String path) {
        this.path = path;
    }

    /**
     * Get validation errors map
     * @return map of field-level validation errors
     */
    public Map<String, String> getValidationErrors() {
        return validationErrors;
    }

    /**
     * Set validation errors
     * @param validationErrors map of field names to error messages
     */
    public void setValidationErrors(Map<String, String> validationErrors) {
        this.validationErrors = validationErrors;
    }
}
