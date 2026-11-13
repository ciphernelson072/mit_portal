package com.sis.repository;
import com.sis.entity.PaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {
    List<PaymentTransaction> findByStudentIdOrderByCreatedAtDesc(Long studentId);
    List<PaymentTransaction> findByFeeIdAndPaymentAttemptIdIsNullAndStatus(Long feeId, String status);
}
