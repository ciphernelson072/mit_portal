CREATE TABLE IF NOT EXISTS exam_permits (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  student_id BIGINT NOT NULL,
  exam_id BIGINT NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  requested_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  approved_at DATETIME NULL,
  approved_by VARCHAR(255) NULL,
  rejected_at DATETIME NULL,
  rejected_by VARCHAR(255) NULL,
  reason VARCHAR(1000) NULL,
  CONSTRAINT uk_exam_permit_student_exam UNIQUE (student_id, exam_id),
  CONSTRAINT fk_exam_permit_student FOREIGN KEY (student_id) REFERENCES students(id),
  CONSTRAINT fk_exam_permit_exam FOREIGN KEY (exam_id) REFERENCES exams(id),
  INDEX idx_exam_permit_student (student_id),
  INDEX idx_exam_permit_status (status)
);
