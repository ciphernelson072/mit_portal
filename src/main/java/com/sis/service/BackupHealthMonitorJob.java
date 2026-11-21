package com.sis.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

@Component
public class BackupHealthMonitorJob {
    private final BackupHealthMonitorService monitor;
    private final AutomationJobLockService locks;
    private final String timeZone;

    public BackupHealthMonitorJob(
            BackupHealthMonitorService monitor,
            AutomationJobLockService locks,
            @Value("${app.automation.time-zone:Africa/Nairobi}") String timeZone) {
        this.monitor = monitor;
        this.locks = locks;
        this.timeZone = timeZone;
    }

    @Scheduled(cron = "0 45 8 * * *", zone = "${app.automation.time-zone:Africa/Nairobi}")
    public void monitorDailyBackupHealth() {
        LocalDate today = LocalDate.now(ZoneId.of(timeZone));
        locks.runIfAcquired("daily-backup-health-monitor", () -> monitor.monitor(today));
    }
}
