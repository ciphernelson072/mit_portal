package com.sis.service;

import com.sis.entity.ExamResult;
import com.sis.repository.ExamResultRepository;
import com.sis.repository.FeeRepository;
import com.sis.repository.FinancialHoldRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@Transactional
public class ResultPublicationService {

    private final ExamResultRepository examResultRepository;
    private final FinancialHoldRepository financialHoldRepository;
    private final FeeRepository feeRepository;

    public ResultPublicationService(ExamResultRepository examResultRepository,
                                   FinancialHoldRepository financialHoldRepository,
                                   FeeRepository feeRepository) {
        this.examResultRepository = examResultRepository;
        this.financialHoldRepository = financialHoldRepository;
        this.feeRepository = feeRepository;
    }

    public ExamResult publishResult(Long resultId, String publishedBy) {
        ExamResult result = examResultRepository.findById(resultId)
                .orElseThrow(() -> new IllegalArgumentException("Exam result not found"));

        if (financialHoldRepository.existsByStudentIdAndActiveTrue(result.getStudentId())) {
            throw new IllegalStateException("Result publication blocked by active financial hold");
        }

        if (feeRepository.findByStudentId(result.getStudentId()).stream().anyMatch(fee -> !fee.isPaid())) {
            throw new IllegalStateException("Result publication blocked by outstanding fees");
        }

        result.setPublished(true);
        result.setPublishedAt(LocalDateTime.now());
        return examResultRepository.save(result);
    }
}
