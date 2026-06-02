package com.sis.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Global Exception Handler for the entire application
 * Catches and handles exceptions globally, logging errors and returning proper HTTP responses
 * Implements centralized error handling across all REST controllers
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    // Logger for error tracking and debugging
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handle validation errors from @Valid annotation
     * Returns 400 Bad Request with detailed field validation errors
     * @param ex MethodArgumentNotValidException from Spring validation
     * @param request the HTTP request
     * @return ResponseEntity with error details
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex,
            WebRequest request) {

        // Extract validation errors from request
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        // Log the validation error
        logger.warn("Validation error occurred: {}", errors);

        // Create error response
        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "Validation failed",
                LocalDateTime.now(),
                request.getDescription(false).replace("uri=", "")
        );
        errorResponse.setValidationErrors(errors);

        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handle authentication failures (bad credentials)
     * Returns 401 Unauthorized when login fails
     * @param ex BadCredentialsException from Spring Security
     * @param request the HTTP request
     * @return ResponseEntity with error details
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentialsException(
            BadCredentialsException ex,
            WebRequest request) {

        logger.warn("Authentication failed: Invalid credentials");

        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.UNAUTHORIZED.value(),
                "Invalid username or password",
                LocalDateTime.now(),
                request.getDescription(false).replace("uri=", "")
        );

        return new ResponseEntity<>(errorResponse, HttpStatus.UNAUTHORIZED);
    }

    /**
     * Handle resource not found exceptions
     * Returns 404 Not Found
     * @param ex RuntimeException when resource doesn't exist
     * @param request the HTTP request
     * @return ResponseEntity with error details
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(
            ResourceNotFoundException ex,
            WebRequest request) {

        logger.warn("Resource not found: {}", ex.getMessage());

        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.NOT_FOUND.value(),
                ex.getMessage(),
                LocalDateTime.now(),
                request.getDescription(false).replace("uri=", "")
        );

        return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
    }

    /**
     * Handle general RuntimeExceptions
     * Returns 400 Bad Request for business logic errors
     * @param ex RuntimeException for custom error messages
     * @param request the HTTP request
     * @return ResponseEntity with error details
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleRuntimeException(
            RuntimeException ex,
            WebRequest request) {

        logger.error("Runtime exception occurred: {}", ex.getMessage(), ex);

        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                ex.getMessage() != null ? ex.getMessage() : "An error occurred",
                LocalDateTime.now(),
                request.getDescription(false).replace("uri=", "")
        );

        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handle all other exceptions not caught by specific handlers
     * Returns 500 Internal Server Error
     * @param ex generic Exception
     * @param request the HTTP request
     * @return ResponseEntity with error details
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGlobalException(
            Exception ex,
            WebRequest request) {

        logger.error("Unexpected exception occurred", ex);

        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "An unexpected error occurred",
                LocalDateTime.now(),
                request.getDescription(false).replace("uri=", "")
        );

        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * Handle access denied exceptions
     * Returns 403 Forbidden
     * @param ex org.springframework.security.access.AccessDeniedException
     * @param request the HTTP request
     * @return ResponseEntity with error details
     */
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(
            org.springframework.security.access.AccessDeniedException ex,
            WebRequest request) {

        logger.warn("Access denied: {}", ex.getMessage());

        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.FORBIDDEN.value(),
                "You do not have permission to access this resource",
                LocalDateTime.now(),
                request.getDescription(false).replace("uri=", "")
        );

        return new ResponseEntity<>(errorResponse, HttpStatus.FORBIDDEN);
    }
}
