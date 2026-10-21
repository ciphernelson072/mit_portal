package com.sis.repository;

import com.sis.entity.EventRegistration;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EventRegistrationRepository extends JpaRepository<EventRegistration, Long> {
    List<EventRegistration> findByStudentId(Long studentId);
    Optional<EventRegistration> findByEventIdAndStudentId(Long eventId, Long studentId);
}
