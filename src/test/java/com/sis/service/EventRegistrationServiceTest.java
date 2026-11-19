package com.sis.service;

import com.sis.entity.CampusEvent;
import com.sis.entity.EventRegistration;
import com.sis.repository.CampusEventRepository;
import com.sis.repository.EventRegistrationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class EventRegistrationServiceTest {
    private CampusEventRepository events;
    private EventRegistrationRepository registrations;
    private EventRegistrationService service;

    @BeforeEach
    void setUp() {
        events = mock(CampusEventRepository.class);
        registrations = mock(EventRegistrationRepository.class);
        service = new EventRegistrationService(events, registrations);
        when(events.findByIdForUpdate(4L)).thenReturn(Optional.of(event(LocalDateTime.now().plusDays(1))));
    }

    @Test
    void registerCreatesUpcomingEventRegistration() {
        when(registrations.findByEventIdAndStudentIdForUpdate(4L, 9L)).thenReturn(Optional.empty());
        when(registrations.save(any(EventRegistration.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EventRegistrationService.RegistrationOutcome outcome = service.register(4L, 9L);

        EventRegistration result = outcome.registration();
        assertEquals(4L, result.getEventId());
        assertEquals(9L, result.getStudentId());
        assertEquals("REGISTERED", result.getStatus());
        assertEquals(true, outcome.created());
    }

    @Test
    void registrationIsRejectedAfterEventStarts() {
        when(events.findByIdForUpdate(4L)).thenReturn(Optional.of(event(LocalDateTime.now().minusMinutes(1))));

        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> service.register(4L, 9L));

        assertEquals(HttpStatus.CONFLICT, HttpStatus.valueOf(error.getStatusCode().value()));
        verify(registrations, never()).save(any(EventRegistration.class));
    }

    @Test
    void duplicateRegistrationIsIdempotent() {
        EventRegistration existing = new EventRegistration(4L, 9L);
        when(registrations.findByEventIdAndStudentIdForUpdate(4L, 9L)).thenReturn(Optional.of(existing));

        EventRegistrationService.RegistrationOutcome outcome = service.register(4L, 9L);
        assertSame(existing, outcome.registration());
        assertEquals(false, outcome.created());
        verify(registrations, never()).save(any(EventRegistration.class));
    }

    @Test
    void retryOfExistingRegistrationRemainsIdempotentAfterEventStarts() {
        when(events.findByIdForUpdate(4L)).thenReturn(Optional.of(event(LocalDateTime.now().minusMinutes(1))));
        EventRegistration existing = new EventRegistration(4L, 9L);
        when(registrations.findByEventIdAndStudentIdForUpdate(4L, 9L)).thenReturn(Optional.of(existing));

        EventRegistrationService.RegistrationOutcome outcome = service.register(4L, 9L);

        assertSame(existing, outcome.registration());
        assertEquals(false, outcome.created());
        verify(registrations, never()).save(any(EventRegistration.class));
    }

    @Test
    void cancellationCanBeFollowedByReRegistrationBeforeStart() {
        EventRegistration existing = new EventRegistration(4L, 9L);
        when(registrations.findByEventIdAndStudentIdForUpdate(4L, 9L))
                .thenReturn(Optional.of(existing)).thenReturn(Optional.of(existing));
        when(registrations.save(existing)).thenReturn(existing);

        assertEquals("CANCELLED", service.cancel(4L, 9L).getStatus());
        EventRegistrationService.RegistrationOutcome outcome = service.register(4L, 9L);
        assertEquals("REGISTERED", outcome.registration().getStatus());
        assertEquals(false, outcome.created());
        verify(registrations, times(2)).save(existing);
    }

    private static CampusEvent event(LocalDateTime startsAt) {
        CampusEvent event = new CampusEvent();
        event.setStartsAt(startsAt);
        return event;
    }
}
