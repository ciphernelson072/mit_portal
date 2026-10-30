ALTER TABLE payment_attempts
  ADD COLUMN provider_transaction_id VARCHAR(80) NULL,
  ADD COLUMN checkout_url VARCHAR(500) NULL,
  ADD COLUMN checkout_expires_at DATETIME NULL;

ALTER TABLE payment_attempts
  ADD UNIQUE KEY uk_payment_provider_transaction_id (provider_transaction_id);
