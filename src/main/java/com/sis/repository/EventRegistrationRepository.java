package com.sis.repository;

import com.sis.entity.EventRegistration;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;

public interface EventRegistrationRepository extends JpaRepository<EventRegistration, Long> {
    List<EventRegistration> findByStudentId(Long studentId);
    Optional<EventRegistration> findByEventIdAndStudentId(Long eventId, Long studentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select registration from EventRegistration registration "
            + "where registration.eventId = :eventId and registration.studentId = :studentId")
    Optional<EventRegistration> findByEventIdAndStudentIdForUpdate(
            @Param("eventId") Long eventId, @Param("studentId") Long studentId);
}
