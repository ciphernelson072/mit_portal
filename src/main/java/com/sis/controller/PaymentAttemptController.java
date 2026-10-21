package com.sis.controller;

import com.sis.dto.CreatePaymentAttemptRequest;
import com.sis.dto.PaymentDestinationView;
import com.sis.dto.RejectPaymentAttemptRequest;
import com.sis.entity.PaymentAttempt;
import com.sis.service.PaymentDestinationService;
import com.sis.service.PaymentAttemptService;
import com.sis.service.SchoolService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/finance/payments")
public class PaymentAttemptController {
    private final PaymentAttemptService paymentService;
    private final PaymentDestinationService destinations;
    private final SchoolService schoolService;

    public PaymentAttemptController(PaymentAttemptService paymentService, PaymentDestinationService destinations,
                                    SchoolService schoolService) {
        this.paymentService = paymentService;
        this.destinations = destinations;
        this.schoolService = schoolService;
    }

    @GetMapping("/destinations")
    @PreAuthorize("hasAuthority('STUDENT')")
    public ResponseEntity<List<PaymentDestinationView>> destinations() {
        return ResponseEntity.ok(destinations.list());
    }

    @PostMapping("/attempts")
    @PreAuthorize("hasAuthority('STUDENT')")
    public ResponseEntity<PaymentAttempt> create(@Valid @RequestBody CreatePaymentAttemptRequest request) {
        return schoolService.getStudentByUsername(username())
                .map(student -> ResponseEntity.status(201).body(paymentService.create(request, student.getId())))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/attempts/mine")
    @PreAuthorize("hasAuthority('STUDENT')")
    public ResponseEntity<List<PaymentAttempt>> mine() {
        return schoolService.getStudentByUsername(username())
                .map(student -> ResponseEntity.ok(paymentService.studentAttempts(student.getId())))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/attempts/pending")
    @PreAuthorize("@permissionSecurity.canConfirmPayments()")
    public ResponseEntity<List<PaymentAttempt>> pending() {
        return ResponseEntity.ok(paymentService.pendingAttempts());
    }

    @PostMapping("/attempts/{id}/confirm")
    @PreAuthorize("@permissionSecurity.canConfirmPayments()")
    public ResponseEntity<PaymentAttempt> confirm(@PathVariable Long id) {
        return ResponseEntity.ok(paymentService.confirm(id, username()));
    }

    @PostMapping("/attempts/{id}/reject")
    @PreAuthorize("@permissionSecurity.canConfirmPayments()")
    public ResponseEntity<PaymentAttempt> reject(@PathVariable Long id,
                                                 @Valid @RequestBody RejectPaymentAttemptRequest request) {
        return ResponseEntity.ok(paymentService.reject(id, username(), request.getReason()));
    }

    private String username() { return SecurityContextHolder.getContext().getAuthentication().getName(); }
}