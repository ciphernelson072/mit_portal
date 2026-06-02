package com.sis.exception;

/**
 * Custom exception for resource not found scenarios
 * Thrown when a requested resource (student, teacher, user, etc.) cannot be found
 * Returns HTTP 404 Not Found response
 */
public class ResourceNotFoundException extends RuntimeException {

    /**
     * Constructor with message
     * @param message description of what resource was not found
     */
    public ResourceNotFoundException(String message) {
        super(message);
    }

    /**
     * Constructor with message and cause
     * @param message description of what resource was not found
     * @param cause the underlying exception cause
     */
    public ResourceNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
