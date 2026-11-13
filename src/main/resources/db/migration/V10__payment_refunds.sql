CREATE TABLE IF NOT EXISTS payment_refunds (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  payment_attempt_id BIGINT NOT NULL,
  fee_id BIGINT NOT NULL,
  student_id BIGINT NOT NULL,
  amount DECIMAL(10,2) NOT NULL,
  reason VARCHAR(1000) NOT NULL,
  processed_by VARCHAR(255) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_refund_attempt FOREIGN KEY (payment_attempt_id) REFERENCES payment_attempts(id),
  CONSTRAINT fk_refund_fee FOREIGN KEY (fee_id) REFERENCES fees(id),
  CONSTRAINT fk_refund_student FOREIGN KEY (student_id) REFERENCES students(id),
  INDEX idx_refund_attempt_status (payment_attempt_id, status)
);