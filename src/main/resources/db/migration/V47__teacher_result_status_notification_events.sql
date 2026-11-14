CREATE TABLE teacher_result_status_notification_events (
    event_key CHAR(64) NOT NULL,
    student_id BIGINT NOT NULL,
    teacher_user_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_teacher_result_status_notification_events PRIMARY KEY (event_key),
    INDEX idx_teacher_result_status_notification_student_teacher
        (student_id, teacher_user_id, created_at)
);
