package com.sis.controller;

import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sis.dto.CreateFeeRequest;
import com.sis.dto.FeeDto;
import com.sis.exception.ResourceNotFoundException;
import com.sis.repository.FeeRepository;
import com.sis.repository.PaymentTransactionRepository;
import com.sis.service.SchoolService;
import com.sis.service.PdfDocumentService;

@RestController
@RequestMapping("/api/fees")
public class FeeController {

    private final SchoolService schoolService;
    private final FeeRepository feeRepository;
    private final PaymentTransactionRepository paymentTransactions;
    private final PdfDocumentService pdfDocuments;

    public FeeController(SchoolService schoolService, FeeRepository feeRepository,
                         PaymentTransactionRepository paymentTransactions, PdfDocumentService pdfDocuments) {
        this.schoolService = schoolService;
        this.feeRepository = feeRepository;
        this.paymentTransactions = paymentTransactions;
        this.pdfDocuments = pdfDocuments;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('STUDENT')")
    public ResponseEntity<List<FeeDto>> getMyFees() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return schoolService.getStudentByUsername(username)
                .map(student -> ResponseEntity.ok(schoolService.getFeesForStudent(student.getId())))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("@permissionSecurity.canManageStudentFees()")
    public ResponseEntity<FeeDto> createFee(@Valid @RequestBody CreateFeeRequest request) {
        var student = schoolService.getStudentById(request.getStudentId())
                .orElseThrow(() -> new ResourceNotFoundException("Student not found with id: " + request.getStudentId()));
        FeeDto created = schoolService.createFee(student, request.getDescription(), request.getAmount(), request.getDueDate());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/{feeId}/pay")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<Map<String, String>> markPaid(@PathVariable Long feeId) {
        return ResponseEntity.status(HttpStatus.GONE).body(Map.of("message",
                "Direct fee settlement is disabled. Submit a payment attempt and confirm it after verification."));
    }

    @GetMapping("/{feeId}/invoice")
    @PreAuthorize("hasAuthority('STUDENT')")
    public ResponseEntity<byte[]> invoice(@PathVariable Long feeId) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return schoolService.getStudentByUsername(username)
                .flatMap(student -> feeRepository.findById(feeId).filter(fee -> fee.getStudent().getId().equals(student.getId())))
                .map(fee -> {
                    try {
                        FeeDto settlement = schoolService.getFeeForStudent(feeId, fee.getStudent().getId())
                                .orElseThrow(() -> new IllegalStateException("Fee settlement details are unavailable"));
                        String status = settlement.getBalance().signum() == 0 ? "SETTLED"
                                : settlement.getAmountPaid().signum() > 0 ? "PARTIALLY PAID" : "PENDING";
                        byte[] pdf = pdfDocuments.invoice("INV-" + fee.getId(), fee.getDescription(),
                                fee.getAmount(), settlement.getAmountPaid(), settlement.getBalance(),
                                fee.getDueDate(), status);
                        return pdfResponse(pdf, "invoice-" + fee.getId() + ".pdf");
                    } catch (java.io.IOException e) {
                        throw new IllegalStateException("Unable to generate invoice", e);
                    }
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/receipts/{paymentId}")
    @PreAuthorize("hasAuthority('STUDENT')")
    public ResponseEntity<byte[]> receipt(@PathVariable Long paymentId) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return schoolService.getStudentByUsername(username)
                .flatMap(student -> paymentTransactions.findById(paymentId).filter(payment -> payment.getStudentId().equals(student.getId())))
                .map(payment -> {
                    try {
                        byte[] pdf = pdfDocuments.receipt("RCT-" + payment.getId(), payment.getId(), payment.getFeeId(),
                                payment.getAmount(), payment.getStatus(), payment.getCreatedAt());
                        return pdfResponse(pdf, "receipt-" + payment.getId() + ".pdf");
                    } catch (java.io.IOException e) {
                        throw new IllegalStateException("Unable to generate receipt", e);
                    }
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private ResponseEntity<byte[]> pdfResponse(byte[] content, String filename) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(content);
    }

}
