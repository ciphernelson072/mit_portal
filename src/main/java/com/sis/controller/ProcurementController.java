package com.sis.controller;

import com.sis.dto.*;
import com.sis.entity.*;
import com.sis.service.ProcurementService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/procurement")
@PreAuthorize("@permissionSecurity.canManageProcurement()")
public class ProcurementController {
    private final ProcurementService procurement;

    public ProcurementController(ProcurementService procurement) { this.procurement = procurement; }

    @PostMapping("/suppliers")
    public ResponseEntity<Supplier> createSupplier(@Valid @RequestBody CreateSupplierRequest request) { return ResponseEntity.status(201).body(procurement.createSupplier(request, username())); }

    @GetMapping("/suppliers")
    public List<Supplier> suppliers() { return procurement.suppliers(); }

    @PostMapping("/requests")
    public ResponseEntity<ProcurementRequest> createRequest(@Valid @RequestBody CreateProcurementRequest request) {
        String requester = SecurityContextHolder.getContext().getAuthentication().getName();
        return ResponseEntity.status(201).body(procurement.createRequest(request, requester));
    }

    @GetMapping("/requests/pending")
    public List<ProcurementRequest> pending() { return procurement.pendingRequests(); }

    @PostMapping("/requests/{id}/review")
    public ResponseEntity<ProcurementRequest> review(@PathVariable Long id, @Valid @RequestBody ReviewProcurementRequest request) {
        return ResponseEntity.ok(procurement.review(id, request, SecurityContextHolder.getContext().getAuthentication().getName()));
    }

    @PostMapping("/assets")
    public ResponseEntity<Asset> createAsset(@Valid @RequestBody CreateAssetRequest request) { return ResponseEntity.status(201).body(procurement.createAsset(request, username())); }

    @GetMapping("/assets")
    public List<Asset> assets() { return procurement.assets(); }

    private String username() { return SecurityContextHolder.getContext().getAuthentication().getName(); }
}