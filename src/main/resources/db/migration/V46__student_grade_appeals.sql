CREATE TABLE grade_appeals (
    id BIGINT NOT NULL AUTO_INCREMENT,
    grade_id BIGINT NOT NULL,
    reason VARCHAR(2000) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'OPEN',
    submitted_at DATETIME(6) NOT NULL,
    consultation_requested_by VARCHAR(255) NULL,
    consultation_requested_at DATETIME(6) NULL,
    teacher_response VARCHAR(2000) NULL,
    teacher_responded_at DATETIME(6) NULL,
    teacher_consulted_by VARCHAR(255) NULL,
    teacher_consulted_at DATETIME(6) NULL,
    decision_by VARCHAR(255) NULL,
    decided_at DATETIME(6) NULL,
    decision_note VARCHAR(2000) NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_grade_appeals PRIMARY KEY (id),
    CONSTRAINT fk_grade_appeals_grade
        FOREIGN KEY (grade_id) REFERENCES grades (id) ON DELETE RESTRICT,
    INDEX idx_grade_appeals_status_submitted (status, submitted_at),
    INDEX idx_grade_appeals_grade_status (grade_id, status)
);
