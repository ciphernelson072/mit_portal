package com.sis.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

@Component
public class AttendanceRiskAlertJob {
    private static final ZoneId INSTITUTION_ZONE = ZoneId.of("Africa/Nairobi");

    private final AttendanceRiskAlertService alerts;
    private final AutomationJobLockService locks;

    public AttendanceRiskAlertJob(AttendanceRiskAlertService alerts, AutomationJobLockService locks) {
        this.alerts = alerts;
        this.locks = locks;
    }

    @Scheduled(cron = "0 30 9 * * *", zone = "Africa/Nairobi")
    public void sendDailyAttendanceRiskAlerts() {
        locks.runIfAcquired("daily-attendance-risk-alerts",
                () -> alerts.sendAttendanceRiskAlerts(LocalDate.now(INSTITUTION_ZONE)));
    }
}
