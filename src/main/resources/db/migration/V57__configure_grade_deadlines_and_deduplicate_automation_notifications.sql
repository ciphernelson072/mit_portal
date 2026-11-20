ALTER TABLE academic_terms
    ADD COLUMN grade_submission_deadline DATE NULL;

ALTER TABLE notifications
    ADD COLUMN automation_key VARCHAR(255) NULL,
    ADD CONSTRAINT uk_notification_automation_key UNIQUE (automation_key);
