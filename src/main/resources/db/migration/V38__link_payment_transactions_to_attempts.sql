ALTER TABLE payment_transactions
  ADD COLUMN payment_attempt_id BIGINT NULL,
  ADD COLUMN payment_channel VARCHAR(30) NULL,
  ADD COLUMN provider_reference VARCHAR(120) NULL,
  ADD COLUMN provider_transaction_id VARCHAR(80) NULL,
  ADD CONSTRAINT uk_payment_transaction_attempt UNIQUE (payment_attempt_id),
  ADD CONSTRAINT fk_payment_transaction_attempt
    FOREIGN KEY (payment_attempt_id) REFERENCES payment_attempts(id);
