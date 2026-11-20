package com.sis.service;

import com.sis.entity.NotificationCategory;
import com.sis.entity.RegistrationStatus;
import com.sis.repository.AttendanceRiskCandidate;
import com.sis.repository.CourseRegistrationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

@Service
@Transactional
public class AttendanceRiskAlertService {
    private static final long WARNING_THRESHOLD_PERCENT = 75;

    private final CourseRegistrationRepository registrations;
    private final NotificationService notifications;

    public AttendanceRiskAlertService(
            CourseRegistrationRepository registrations, NotificationService notifications) {
        this.registrations = registrations;
        this.notifications = notifications;
    }

    public int sendAttendanceRiskAlerts(LocalDate today) {
        List<AttendanceRiskCandidate> candidates = registrations.findCurrentTermAttendanceRiskCandidates(
                RegistrationStatus.REGISTERED, today);
        int alerts = 0;
        for (AttendanceRiskCandidate candidate : candidates) {
            long total = candidate.getTotalSessions();
            long present = candidate.getPresentSessions();
            if (total == 0 || present * 100 >= total * WARNING_THRESHOLD_PERCENT) {
                continue;
            }
            double percentage = present * 100.0 / total;
            String course = candidate.getCourseCode() == null || candidate.getCourseCode().isBlank()
                    ? candidate.getCourseName() : candidate.getCourseCode();
            boolean created = notifications.createAutomated(
                    "attendance-risk:" + candidate.getTermId() + ":"
                            + candidate.getStudentId() + ":" + candidate.getCourseId(),
                    candidate.getUserId(),
                    "Attendance below 75%",
                    String.format(Locale.ROOT,
                            "Your attendance in %s is %.1f%% (%d of %d recorded sessions). "
                                    + "Please contact your instructor if you need support.",
                            course, percentage, present, total),
                    NotificationCategory.ATTENDANCE);
            if (created) {
                alerts++;
            }
        }
        return alerts;
    }
}
