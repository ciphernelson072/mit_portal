CREATE TABLE grade_correction_requests (
    id BIGINT NOT NULL AUTO_INCREMENT,
    grade_id BIGINT NOT NULL,
    reason VARCHAR(2000) NOT NULL,
    previous_snapshot LONGTEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'AUTHORIZED',
    authorized_by VARCHAR(255) NOT NULL,
    authorized_at DATETIME(6) NOT NULL,
    submitted_at DATETIME(6) NULL,
    completed_at DATETIME(6) NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_grade_correction_requests PRIMARY KEY (id),
    CONSTRAINT fk_grade_correction_grade
        FOREIGN KEY (grade_id) REFERENCES grades (id) ON DELETE RESTRICT,
    INDEX idx_grade_correction_grade_status (grade_id, status)
);
