package com.sis.service;

import com.sis.entity.NotificationCategory;
import com.sis.entity.RegistrationStatus;
import com.sis.repository.AttendanceRiskCandidate;
import com.sis.repository.CourseRegistrationRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AttendanceRiskAlertServiceTest {
    @Test
    void alertsOnlyRegisteredStudentsBelowTheAttendanceThreshold() {
        CourseRegistrationRepository registrations = mock(CourseRegistrationRepository.class);
        NotificationService notifications = mock(NotificationService.class);
        AttendanceRiskCandidate candidate = mock(AttendanceRiskCandidate.class);
        when(candidate.getTermId()).thenReturn(4L);
        when(candidate.getStudentId()).thenReturn(8L);
        when(candidate.getUserId()).thenReturn(12L);
        when(candidate.getCourseId()).thenReturn(16L);
        when(candidate.getCourseCode()).thenReturn("CSC101");
        when(candidate.getPresentSessions()).thenReturn(2L);
        when(candidate.getTotalSessions()).thenReturn(3L);
        when(registrations.findCurrentTermAttendanceRiskCandidates(
                RegistrationStatus.REGISTERED, LocalDate.of(2026, 11, 19)))
                .thenReturn(List.of(candidate));
        when(notifications.createAutomated(
                "attendance-risk:4:8:16", 12L, "Attendance below 75%",
                "Your attendance in CSC101 is 66.7% (2 of 3 recorded sessions). "
                        + "Please contact your instructor if you need support.",
                NotificationCategory.ATTENDANCE)).thenReturn(true);

        int count = new AttendanceRiskAlertService(registrations, notifications)
                .sendAttendanceRiskAlerts(LocalDate.of(2026, 11, 19));

        assertEquals(1, count);
        verify(registrations).findCurrentTermAttendanceRiskCandidates(
                RegistrationStatus.REGISTERED, LocalDate.of(2026, 11, 19));
    }

    @Test
    void doesNotRepeatAnExistingTermCourseAlert() {
        CourseRegistrationRepository registrations = mock(CourseRegistrationRepository.class);
        NotificationService notifications = mock(NotificationService.class);
        AttendanceRiskCandidate candidate = mock(AttendanceRiskCandidate.class);
        when(candidate.getTermId()).thenReturn(4L);
        when(candidate.getStudentId()).thenReturn(8L);
        when(candidate.getUserId()).thenReturn(12L);
        when(candidate.getCourseId()).thenReturn(16L);
        when(candidate.getCourseCode()).thenReturn("CSC101");
        when(candidate.getPresentSessions()).thenReturn(0L);
        when(candidate.getTotalSessions()).thenReturn(1L);
        when(registrations.findCurrentTermAttendanceRiskCandidates(
                RegistrationStatus.REGISTERED, LocalDate.of(2026, 11, 19)))
                .thenReturn(List.of(candidate));
        when(notifications.createAutomated(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any())).thenReturn(false);

        int count = new AttendanceRiskAlertService(registrations, notifications)
                .sendAttendanceRiskAlerts(LocalDate.of(2026, 11, 19));

        assertEquals(0, count);
    }
}
