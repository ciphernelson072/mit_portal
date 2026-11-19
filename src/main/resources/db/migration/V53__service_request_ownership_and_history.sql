ALTER TABLE service_requests
    -- Existing records retain a null last-updated time because prior changes
    -- did not store that timestamp and cannot be reconstructed.
    ADD COLUMN updated_at DATETIME(6) NULL,
    ADD COLUMN assigned_admin_id BIGINT NULL,
    ADD COLUMN assigned_at DATETIME(6) NULL;

ALTER TABLE service_requests
    ADD INDEX idx_service_request_assigned_admin (assigned_admin_id),
    ADD CONSTRAINT fk_service_request_assigned_admin
        FOREIGN KEY (assigned_admin_id) REFERENCES users(id) ON DELETE SET NULL;

CREATE TABLE service_request_events (
    id BIGINT NOT NULL AUTO_INCREMENT,
    service_request_id BIGINT NOT NULL,
    actor_user_id BIGINT NULL,
    actor_username VARCHAR(255) NOT NULL,
    event_type VARCHAR(30) NOT NULL,
    from_status VARCHAR(30) NULL,
    to_status VARCHAR(30) NULL,
    from_assignee_id BIGINT NULL,
    to_assignee_id BIGINT NULL,
    from_assignee_username VARCHAR(255) NULL,
    to_assignee_username VARCHAR(255) NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_service_request_events PRIMARY KEY (id),
    CONSTRAINT fk_service_request_event_request
        FOREIGN KEY (service_request_id) REFERENCES service_requests(id) ON DELETE CASCADE,
    INDEX idx_service_request_event_request_created (service_request_id, created_at)
);
