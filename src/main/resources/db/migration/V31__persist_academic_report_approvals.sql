CREATE TABLE academic_report_approvals (
    student_id BIGINT NOT NULL,
    approved_by VARCHAR(255) NOT NULL,
    approved_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_academic_report_approvals PRIMARY KEY (student_id),
    CONSTRAINT fk_academic_report_approvals_student
        FOREIGN KEY (student_id) REFERENCES students (id) ON DELETE CASCADE
);
