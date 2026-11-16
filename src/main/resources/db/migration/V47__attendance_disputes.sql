CREATE TABLE attendance_disputes (
    id BIGINT NOT NULL AUTO_INCREMENT,
    attendance_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    reason VARCHAR(2000) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    resolution VARCHAR(2000),
    requested_present BOOLEAN NOT NULL,
    reviewed_by BIGINT,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    reviewed_at DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_attendance_dispute_attendance FOREIGN KEY (attendance_id) REFERENCES attendance(id),
    CONSTRAINT fk_attendance_dispute_student FOREIGN KEY (student_id) REFERENCES students(id),
    CONSTRAINT fk_attendance_dispute_reviewer FOREIGN KEY (reviewed_by) REFERENCES users(id),
    INDEX idx_attendance_dispute_student (student_id),
    INDEX idx_attendance_dispute_status (status),
    INDEX idx_attendance_dispute_attendance (attendance_id)
);
