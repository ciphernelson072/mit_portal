CREATE TABLE IF NOT EXISTS financial_holds (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  student_id BIGINT NOT NULL,
  reason VARCHAR(1000) NOT NULL,
  created_by VARCHAR(255) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  active TINYINT(1) NOT NULL DEFAULT 1,
  released_at DATETIME NULL,
  CONSTRAINT fk_financial_hold_student FOREIGN KEY (student_id) REFERENCES students(id),
  INDEX idx_financial_hold_student_active (student_id, active)
);