package com.sis.controller;

import com.sis.dto.CreateFeeWaiverRequest;
import com.sis.entity.FeeWaiver;
import com.sis.service.FeeWaiverService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/finance/waivers")
@PreAuthorize("@permissionSecurity.canApproveFeeWaivers()")
public class FeeWaiverController {
    private final FeeWaiverService waiverService;

    public FeeWaiverController(FeeWaiverService waiverService) { this.waiverService = waiverService; }

    @PostMapping
    public ResponseEntity<FeeWaiver> create(@Valid @RequestBody CreateFeeWaiverRequest request) {
        return ResponseEntity.status(201).body(waiverService.create(request,
                SecurityContextHolder.getContext().getAuthentication().getName()));
    }
}