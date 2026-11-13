CREATE TABLE IF NOT EXISTS payment_allocations (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  payment_attempt_id BIGINT NOT NULL UNIQUE,
  fee_id BIGINT NOT NULL,
  amount DECIMAL(10,2) NOT NULL,
  allocated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_allocation_attempt FOREIGN KEY (payment_attempt_id) REFERENCES payment_attempts(id),
  CONSTRAINT fk_allocation_fee FOREIGN KEY (fee_id) REFERENCES fees(id),
  INDEX idx_allocation_fee (fee_id)
);

CREATE TABLE IF NOT EXISTS fee_waivers (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  fee_id BIGINT NOT NULL,
  amount DECIMAL(10,2) NOT NULL,
  reason VARCHAR(1000) NOT NULL,
  approved_by VARCHAR(255) NOT NULL,
  approved_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  active TINYINT(1) NOT NULL DEFAULT 1,
  CONSTRAINT fk_waiver_fee FOREIGN KEY (fee_id) REFERENCES fees(id),
  INDEX idx_waiver_fee_active (fee_id, active)
);