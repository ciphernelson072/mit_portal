package com.sis.service;

import com.sis.entity.FinancialHold;
import com.sis.entity.Student;
import com.sis.repository.FeeRepository;
import com.sis.repository.FinancialHoldRepository;
import com.sis.repository.StudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@Transactional
public class AcademicClearanceService {

    private final FinancialHoldRepository financialHoldRepository;
    private final FeeRepository feeRepository;
    private final StudentRepository studentRepository;

    public AcademicClearanceService(FinancialHoldRepository financialHoldRepository,
                                   FeeRepository feeRepository,
                                   StudentRepository studentRepository) {
        this.financialHoldRepository = financialHoldRepository;
        this.feeRepository = feeRepository;
        this.studentRepository = studentRepository;
    }

    @Transactional(readOnly = true)
    public boolean canStudentViewResults(Long studentId) {
        if (studentId == null) {
            return false;
        }
        if (financialHoldRepository.existsByStudentIdAndActiveTrue(studentId)) {
            return false;
        }
        return !hasOutstandingFees(studentId);
    }

    @Transactional(readOnly = true)
    public boolean canStudentRegister(Long studentId) {
        if (studentId == null) {
            return false;
        }
        if (financialHoldRepository.existsByStudentIdAndActiveTrue(studentId)) {
            return false;
        }
        return !hasOutstandingFees(studentId);
    }

    @Transactional(readOnly = true)
    public boolean hasOutstandingFees(Long studentId) {
        if (studentId == null) {
            return false;
        }
        return feeRepository.findByStudentId(studentId).stream()
                .anyMatch(fee -> !fee.isPaid() && fee.getAmount() != null && fee.getAmount().compareTo(BigDecimal.ZERO) > 0);
    }

    @Transactional(readOnly = true)
    public boolean hasActiveFinancialHold(Long studentId) {
        return studentId != null && financialHoldRepository.existsByStudentIdAndActiveTrue(studentId);
    }

    @Transactional(readOnly = true)
    public List<FinancialHold> activeHolds(Long studentId) {
        if (studentId == null) {
            return List.of();
        }
        return financialHoldRepository.findByStudentIdAndActiveTrueOrderByCreatedAtDesc(studentId);
    }

    @Transactional(readOnly = true)
    public Student findStudent(Long studentId) {
        return studentRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Student not found"));
    }
}
