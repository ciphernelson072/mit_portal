package com.sis.controller;

import com.sis.dto.CreateRefundRequest;
import com.sis.dto.CompleteRefundRequest;
import com.sis.dto.RejectRefundRequest;
import com.sis.entity.PaymentRefund;
import com.sis.service.RefundService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/finance/refunds")
@PreAuthorize("@permissionSecurity.canRefundPayments()")
public class RefundController {
    private final RefundService refunds;

    public RefundController(RefundService refunds) { this.refunds = refunds; }

    @GetMapping
    public ResponseEntity<List<PaymentRefund>> pending() {
        return ResponseEntity.ok(refunds.pending());
    }

    @PostMapping
    public ResponseEntity<PaymentRefund> create(@Valid @RequestBody CreateRefundRequest request) {
        return ResponseEntity.status(201).body(refunds.refund(request,
                SecurityContextHolder.getContext().getAuthentication().getName()));
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<PaymentRefund> complete(@PathVariable Long id,
                                                   @Valid @RequestBody CompleteRefundRequest request) {
        return ResponseEntity.ok(refunds.complete(id,
                SecurityContextHolder.getContext().getAuthentication().getName(), request.getRefundReference()));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<PaymentRefund> reject(@PathVariable Long id,
                                                 @Valid @RequestBody RejectRefundRequest request) {
        return ResponseEntity.ok(refunds.reject(id,
                SecurityContextHolder.getContext().getAuthentication().getName(), request.getReason()));
    }
}