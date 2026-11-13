/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.sis.controller;


import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author user
 */
public class Exception {
    


@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleConflict(DataIntegrityViolationException e) {
        Map<String, String> response = new HashMap<>();
        
        // Detect if the error is specifically a duplicate entry from MySQL
        if (e.getRootCause() != null && e.getRootCause().getMessage().contains("Duplicate entry")) {
            response.put("message", "This username is already taken. Please choose another.");
        } else {
            response.put("message", "Database constraint violation occurred.");
        }
        
        // Return a 409 Conflict status with the JSON map
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }
}
    
}
