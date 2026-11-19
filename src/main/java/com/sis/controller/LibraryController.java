package com.sis.controller;

import com.sis.dto.CreateLibraryItemRequest;
import com.sis.dto.CreateLibraryLoanRequest;
import com.sis.entity.LibraryItem;
import com.sis.entity.LibraryLoan;
import com.sis.service.LibraryService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/library")
public class LibraryController {
    private final LibraryService library;

    public LibraryController(LibraryService library) { this.library = library; }

    @GetMapping("/catalog")
    @PreAuthorize("isAuthenticated()")
    public List<LibraryItem> catalog() { return library.catalog(); }

    @PostMapping("/items")
    @PreAuthorize("@permissionSecurity.canManageLibrary()")
    public ResponseEntity<LibraryItem> createItem(@Valid @RequestBody CreateLibraryItemRequest request) {
        return ResponseEntity.status(201).body(library.createItem(request));
    }

    @PostMapping("/loans")
    @PreAuthorize("@permissionSecurity.canManageLibrary()")
    public ResponseEntity<LibraryLoan> issue(@Valid @RequestBody CreateLibraryLoanRequest request) {
        return ResponseEntity.status(201).body(library.issue(request));
    }

    @GetMapping("/loans/mine")
    @PreAuthorize("hasAuthority('STUDENT')")
    public List<LibraryLoan> mine(org.springframework.security.core.Authentication authentication) {
        library.refreshOverdue();
        return library.studentLoans(authentication.getName());
    }

    @PostMapping("/loans/{id}/return")
    @PreAuthorize("@permissionSecurity.canManageLibrary()")
    public ResponseEntity<LibraryLoan> returnLoan(@PathVariable Long id) {
        return ResponseEntity.ok(library.returnLoan(id));
    }
}