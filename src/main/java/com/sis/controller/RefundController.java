package com.sis.controller;

import com.sis.dto.CreateRefundRequest;
import com.sis.entity.PaymentRefund;
import com.sis.service.RefundService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/finance/refunds")
@PreAuthorize("@permissionSecurity.canRefundPayments()")
public class RefundController {
    private final RefundService refunds;

    public RefundController(RefundService refunds) { this.refunds = refunds; }

    @PostMapping
    public ResponseEntity<PaymentRefund> create(@Valid @RequestBody CreateRefundRequest request) {
        return ResponseEntity.status(201).body(refunds.refund(request,
                SecurityContextHolder.getContext().getAuthentication().getName()));
    }
}