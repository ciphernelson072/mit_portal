CREATE TABLE IF NOT EXISTS email_notification_deliveries (
    id BIGINT NOT NULL AUTO_INCREMENT,
    notification_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempt_count INT NOT NULL DEFAULT 0,
    next_attempt_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_error VARCHAR(512) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at DATETIME NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_email_delivery_notification UNIQUE (notification_id),
    CONSTRAINT fk_email_delivery_notification FOREIGN KEY (notification_id)
        REFERENCES notifications (id) ON DELETE CASCADE,
    INDEX idx_email_delivery_due (status, next_attempt_at, id)
);
