CREATE TABLE IF NOT EXISTS bank_reconciliation_entries (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  provider_reference VARCHAR(120) NOT NULL UNIQUE,
  statement_amount DECIMAL(10,2) NOT NULL,
  statement_date DATE NOT NULL,
  matched_payment_attempt_id BIGINT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'UNMATCHED',
  reconciled_by VARCHAR(255),
  reconciled_at DATETIME NULL,
  CONSTRAINT fk_reconciliation_attempt FOREIGN KEY (matched_payment_attempt_id) REFERENCES payment_attempts(id),
  INDEX idx_reconciliation_status_date (status, statement_date)
);