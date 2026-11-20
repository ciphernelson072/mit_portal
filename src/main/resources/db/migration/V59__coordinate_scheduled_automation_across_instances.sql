CREATE TABLE automation_job_locks (
    job_key VARCHAR(100) NOT NULL,
    lock_token VARCHAR(36) NULL,
    locked_until DATETIME NOT NULL DEFAULT '1970-01-01 00:00:00',
    PRIMARY KEY (job_key)
);

INSERT INTO automation_job_locks (job_key)
VALUES ('daily-deadline-reminders'), ('daily-attendance-risk-alerts');
