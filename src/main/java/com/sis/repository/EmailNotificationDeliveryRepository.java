package com.sis.repository;

import com.sis.entity.EmailDeliveryStatus;
import com.sis.entity.EmailNotificationDelivery;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface EmailNotificationDeliveryRepository extends JpaRepository<EmailNotificationDelivery, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select delivery from EmailNotificationDelivery delivery "
            + "join fetch delivery.notification "
            + "where delivery.status in :statuses and delivery.nextAttemptAt <= :now "
            + "order by delivery.nextAttemptAt asc, delivery.id asc")
    List<EmailNotificationDelivery> findDueForUpdate(
            @Param("statuses") List<EmailDeliveryStatus> statuses,
            @Param("now") LocalDateTime now,
            Pageable pageable);

    long countByStatus(EmailDeliveryStatus status);

    @EntityGraph(attributePaths = "notification")
    List<EmailNotificationDelivery> findTop100ByOrderByCreatedAtDesc();
}
