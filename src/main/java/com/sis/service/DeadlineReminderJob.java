package com.sis.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DeadlineReminderJob {
    private final DeadlineReminderService reminders;
    private final AutomationJobLockService locks;

    public DeadlineReminderJob(DeadlineReminderService reminders, AutomationJobLockService locks) {
        this.reminders = reminders;
        this.locks = locks;
    }

    @Scheduled(cron = "0 0 9 * * *", zone = "${app.automation.time-zone:Africa/Nairobi}")
    public void sendDueDeadlineReminders() {
        locks.runIfAcquired("daily-deadline-reminders", reminders::sendDailyReminders);
    }
}
