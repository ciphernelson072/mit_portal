package com.sis.service;

import com.sis.dto.AdminFeeDto;
import com.sis.dto.FeeDto;
import com.sis.entity.Fee;
import com.sis.entity.PaymentTransaction;
import com.sis.entity.Student;
import com.sis.repository.FeeRepository;
import com.sis.repository.PaymentTransactionRepository;
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
    private final PaymentTransactionRepository transactionRepository;

    public FeeService(FeeRepository feeRepository, PaymentTransactionRepository transactionRepository) {
        this.feeRepository = feeRepository;
        this.transactionRepository = transactionRepository;
    }

    public List<FeeDto> getFeesForStudent(Long studentId) {
        if (studentId == null) return Collections.emptyList();
        return feeRepository.findByStudentId(studentId).stream()
                .map(fee -> new FeeDto(
                        fee.getId(),
                        fee.getDescription(),
                        fee.getAmount(),
                        fee.isPaid(),
                        fee.getDueDate(),
                        fee.getPaidDate()))
                .collect(Collectors.toList());
    }

        public List<AdminFeeDto> getAllFees() {
        return feeRepository.findAll().stream()
            .map(fee -> new AdminFeeDto(fee.getId(), fee.getStudent().getId(),
                fee.getStudent().getFullName(), fee.getStudent().getUser().getUsername(),
                fee.getDescription(), fee.getAmount(), fee.isPaid(),
                fee.getDueDate(), fee.getPaidDate()))
            .collect(Collectors.toList());
        }

    public FeeDto createFee(Student student, String description, BigDecimal amount, LocalDate dueDate) {
        Fee saved = feeRepository.save(new Fee(student, description, amount, dueDate));
        return new FeeDto(saved.getId(), saved.getDescription(), saved.getAmount(), saved.isPaid(), saved.getDueDate(), saved.getPaidDate());
    }

    public void markFeePaid(Long feeId) {
        feeRepository.findById(feeId).ifPresent(fee -> {
            fee.setPaid(true);
            fee.setPaidDate(LocalDate.now());
            feeRepository.save(fee);
        });
    }

    public Optional<FeeDto> payFeeForStudent(Long feeId, Long studentId) {
        return feeRepository.findById(feeId)
                .filter(fee -> fee.getStudent().getId().equals(studentId))
                .map(fee -> {
                    fee.setPaid(true);
                    fee.setPaidDate(LocalDate.now());
                    Fee saved = feeRepository.save(fee);
                    transactionRepository.save(new PaymentTransaction(saved.getId(), studentId, saved.getAmount()));
                    return new FeeDto(saved.getId(), saved.getDescription(), saved.getAmount(),
                            saved.isPaid(), saved.getDueDate(), saved.getPaidDate());
                });
    }

}
