package com.sis.repository;

import com.sis.entity.PaymentAttempt;
import com.sis.entity.PaymentAttemptStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, Long> {
    List<PaymentAttempt> findByFeeIdAndStudentIdOrderByCreatedAtDesc(Long feeId, Long studentId);
    List<PaymentAttempt> findByStudentIdOrderByCreatedAtDesc(Long studentId);
    List<PaymentAttempt> findByStatusOrderByCreatedAtAsc(com.sis.entity.PaymentAttemptStatus status);
    Optional<PaymentAttempt> findByProviderReference(String providerReference);
    Optional<PaymentAttempt> findByProviderReferenceAndPaymentChannel(
            String providerReference, com.sis.entity.PaymentChannel paymentChannel);
    Optional<PaymentAttempt> findByProviderReferenceAndStudentIdAndPaymentChannel(
            String providerReference, Long studentId, com.sis.entity.PaymentChannel paymentChannel);
    Optional<PaymentAttempt> findFirstByFeeIdAndStudentIdAndPaymentChannelAndStatusOrderByCreatedAtDesc(
            Long feeId, Long studentId, com.sis.entity.PaymentChannel paymentChannel, PaymentAttemptStatus status);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PaymentAttempt p where p.id = :id")
    Optional<PaymentAttempt> findByIdForUpdate(@Param("id") Long id);
    long countByFeeIdAndStudentIdAndStatus(Long feeId, Long studentId, PaymentAttemptStatus status);

    @Query("select coalesce(sum(attempt.amount), 0) from PaymentAttempt attempt where attempt.feeId = :feeId and attempt.status = :status")
    BigDecimal sumAmountByFeeIdAndStatus(@Param("feeId") Long feeId, @Param("status") PaymentAttemptStatus status);
}