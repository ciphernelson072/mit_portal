package com.sis.service;

import com.sis.dto.AdminFeeDto;
import com.sis.dto.FeeDto;
import com.sis.entity.Fee;
import com.sis.entity.Student;
import com.sis.repository.FeeRepository;
import com.sis.repository.PaymentAllocationRepository;
import com.sis.repository.PaymentRefundRepository;
import com.sis.repository.FeeWaiverRepository;
import com.sis.repository.PaymentTransactionRepository;
import com.sis.entity.PaymentAllocation;
import com.sis.entity.PaymentRefund;
import com.sis.entity.RefundStatus;
import com.sis.entity.FeeWaiver;
import com.sis.entity.PaymentTransaction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/** Student fees - split out of the former monolithic SchoolService. */
@Service
@Transactional
public class FeeService {

    private final FeeRepository feeRepository;
    private final PaymentTransactionRepository transactions;
    private final PaymentAllocationRepository allocations;
    private final PaymentRefundRepository refunds;
    private final FeeWaiverRepository waivers;

    public FeeService(FeeRepository feeRepository, PaymentTransactionRepository transactions,
                      PaymentAllocationRepository allocations, PaymentRefundRepository refunds,
                      FeeWaiverRepository waivers) {
        this.feeRepository = feeRepository;
        this.transactions = transactions;
        this.allocations = allocations;
        this.refunds = refunds;
        this.waivers = waivers;
    }

    public List<FeeDto> getFeesForStudent(Long studentId) {
        if (studentId == null) return Collections.emptyList();
        return feeRepository.findByStudentId(studentId).stream()
                .map(this::toStudentFeeDto)
                .collect(Collectors.toList());
    }

    public Optional<FeeDto> getFeeForStudent(Long feeId, Long studentId) {
        if (feeId == null || studentId == null) return Optional.empty();
        return feeRepository.findById(feeId)
                .filter(fee -> fee.getStudent().getId().equals(studentId))
                .map(this::toStudentFeeDto);
    }

    public List<AdminFeeDto> getAllFees() {
        return feeRepository.findAllWithStudentAndUser().stream()
                .map(fee -> {
                    AdminFeeDto dto = new AdminFeeDto(fee.getId(), fee.getStudent().getId(),
                            fee.getStudent().getFullName(), fee.getStudent().getUser().getUsername(),
                            fee.getDescription(), fee.getAmount(), fee.isPaid(),
                            fee.getDueDate(), fee.getPaidDate());
                    setSettlementAmounts(dto, fee);
                    return dto;
                })
                .collect(Collectors.toList());
    }

    public FeeDto createFee(Student student, String description, BigDecimal amount, LocalDate dueDate) {
        Fee saved = feeRepository.save(new Fee(student, description, amount, dueDate));
        return toStudentFeeDto(saved);
    }

    private void setSettlementAmounts(AdminFeeDto dto, Fee fee) {
        FeeSettlement settlement = feeSettlement(fee);
        dto.setSettlementAmounts(settlement.amountPaid, settlement.balance);
    }

    private FeeSettlement feeSettlement(Fee fee) {
        BigDecimal allocated = allocations.findByFeeIdOrderByAllocatedAtAsc(fee.getId()).stream()
                .map(PaymentAllocation::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal legacyAllocated = transactions
                .findByFeeIdAndPaymentAttemptIdIsNullAndStatus(fee.getId(), "COMPLETED").stream()
                .map(PaymentTransaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal refunded = refunds.findByFeeIdAndStatus(fee.getId(), RefundStatus.COMPLETED).stream()
                .map(PaymentRefund::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal waived = waivers.findByFeeIdAndActiveTrueOrderByApprovedAtDesc(fee.getId()).stream()
                .map(FeeWaiver::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal amountPaid = allocated.add(legacyAllocated).subtract(refunded).max(BigDecimal.ZERO);
        BigDecimal balance = fee.getAmount().subtract(amountPaid).subtract(waived).max(BigDecimal.ZERO);
        return new FeeSettlement(amountPaid, balance);
    }

    public void markFeePaid(Long feeId) {
        throw new IllegalStateException("Direct fee settlement is disabled. Confirm a verified payment attempt instead.");
    }

    public Optional<FeeDto> payFeeForStudent(Long feeId, Long studentId) {
        throw new IllegalStateException("Direct fee settlement is disabled. Submit a payment attempt instead.");
    }

    private FeeDto toStudentFeeDto(Fee fee) {
        FeeDto dto = new FeeDto(fee.getId(), fee.getDescription(), fee.getAmount(),
                fee.isPaid(), fee.getDueDate(), fee.getPaidDate());
        FeeSettlement settlement = feeSettlement(fee);
        dto.setSettlementAmounts(settlement.amountPaid, settlement.balance);
        return dto;
    }

    private static final class FeeSettlement {
        private final BigDecimal amountPaid;
        private final BigDecimal balance;

        private FeeSettlement(BigDecimal amountPaid, BigDecimal balance) {
            this.amountPaid = amountPaid;
            this.balance = balance;
        }
    }

}
