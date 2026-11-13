package com.sis.controller;

import com.sis.dto.CreateReconciliationEntryRequest;
import com.sis.entity.BankReconciliationEntry;
import com.sis.entity.ReconciliationStatus;
import com.sis.service.ReconciliationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/finance/reconciliation")
@PreAuthorize("@permissionSecurity.canReconcilePayments()")
public class ReconciliationController {
    private final ReconciliationService reconciliation;

    public ReconciliationController(ReconciliationService reconciliation) { this.reconciliation = reconciliation; }

    @PostMapping("/entries")
    public ResponseEntity<BankReconciliationEntry> importEntry(@Valid @RequestBody CreateReconciliationEntryRequest request) {
        return ResponseEntity.status(201).body(reconciliation.importEntry(request, username()));
    }

    @GetMapping("/entries")
    public List<BankReconciliationEntry> entries(@RequestParam(required = false) ReconciliationStatus status) {
        return reconciliation.list(status);
    }

    @GetMapping("/summary")
    public Map<ReconciliationStatus, Long> summary() { return reconciliation.summary(); }

    private String username() { return SecurityContextHolder.getContext().getAuthentication().getName(); }
}