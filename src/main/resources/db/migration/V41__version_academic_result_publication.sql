CREATE TABLE academic_result_snapshots (
    id BIGINT NOT NULL AUTO_INCREMENT,
    student_id BIGINT NOT NULL,
    results_version CHAR(64) NOT NULL,
    snapshot_payload LONGTEXT NOT NULL,
    approved_by VARCHAR(255) NOT NULL,
    approved_at DATETIME(6) NOT NULL,
    published_by VARCHAR(255) NULL,
    published_at DATETIME(6) NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_academic_result_snapshots PRIMARY KEY (id),
    CONSTRAINT uk_academic_result_snapshot_version UNIQUE (student_id, results_version),
    CONSTRAINT fk_academic_result_snapshot_student
        FOREIGN KEY (student_id) REFERENCES students (id) ON DELETE CASCADE,
    INDEX idx_academic_result_snapshot_student_published (student_id, published_at)
);
