package com.sis.controller;

import com.sis.dto.CreateFeeRequest;
import com.sis.dto.FeeDto;
import com.sis.entity.Fee;
import com.sis.service.SchoolService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/fees")
public class FeeController {

    private final SchoolService schoolService;

    public FeeController(SchoolService schoolService) {
        this.schoolService = schoolService;
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
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<Fee> createFee(@RequestBody CreateFeeRequest request) {
        return schoolService.getStudentById(request.getStudentId())
                .map(student -> ResponseEntity.ok(schoolService.createFee(student, request.getDescription(), request.getAmount(), request.getDueDate())))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{feeId}/pay")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<?> markPaid(@PathVariable Long feeId) {
        schoolService.markFeePaid(feeId);
        return ResponseEntity.ok().build();
    }
}