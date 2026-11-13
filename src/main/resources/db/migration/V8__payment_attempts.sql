CREATE TABLE IF NOT EXISTS payment_attempts (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  fee_id BIGINT NOT NULL,
  student_id BIGINT NOT NULL,
  amount DECIMAL(10,2) NOT NULL,
  provider_reference VARCHAR(120) NOT NULL UNIQUE,
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  confirmed_at DATETIME NULL,
  CONSTRAINT fk_payment_attempt_fee FOREIGN KEY (fee_id) REFERENCES fees(id),
  CONSTRAINT fk_payment_attempt_student FOREIGN KEY (student_id) REFERENCES students(id),
  INDEX idx_payment_attempt_student (student_id),
  INDEX idx_payment_attempt_fee_status (fee_id, status)
);