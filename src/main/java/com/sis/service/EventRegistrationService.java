package com.sis.service;

import com.sis.entity.CampusEvent;
import com.sis.entity.EventRegistration;
import com.sis.repository.CampusEventRepository;
import com.sis.repository.EventRegistrationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

@Service
public class EventRegistrationService {
    public record RegistrationOutcome(EventRegistration registration, boolean created) {}

    private final CampusEventRepository events;
    private final EventRegistrationRepository registrations;

    public EventRegistrationService(CampusEventRepository events, EventRegistrationRepository registrations) {
        this.events = events;
        this.registrations = registrations;
    }

    @Transactional
    public RegistrationOutcome register(Long eventId, Long studentId) {
        CampusEvent event = events.findByIdForUpdate(eventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Campus event not found"));
        EventRegistration registration = registrations.findByEventIdAndStudentIdForUpdate(eventId, studentId)
                .orElse(null);
        if (registration == null) {
            ensureNotStarted(event);
            return new RegistrationOutcome(registrations.save(new EventRegistration(eventId, studentId)), true);
        }
        if (!"REGISTERED".equals(registration.getStatus())) {
            ensureNotStarted(event);
            registration.setStatus("REGISTERED");
            registration.setRegisteredAt(LocalDateTime.now());
            return new RegistrationOutcome(registrations.save(registration), false);
        }
        return new RegistrationOutcome(registration, false);
    }

    @Transactional
    public EventRegistration cancel(Long eventId, Long studentId) {
        CampusEvent event = events.findByIdForUpdate(eventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Campus event not found"));
        ensureNotStarted(event);
        EventRegistration registration = registrations.findByEventIdAndStudentIdForUpdate(eventId, studentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event registration not found"));
        if (!"REGISTERED".equals(registration.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Event registration is already cancelled");
        }
        registration.setStatus("CANCELLED");
        return registrations.save(registration);
    }

    private static void ensureNotStarted(CampusEvent event) {
        if (event.getStartsAt() == null || !event.getStartsAt().isAfter(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Event registration is closed because the event has started");
        }
    }
}
