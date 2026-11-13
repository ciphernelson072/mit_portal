ALTER TABLE payment_attempts
  ADD COLUMN reviewed_by VARCHAR(100) NULL,
  ADD COLUMN reviewed_at DATETIME NULL,
  ADD COLUMN review_note VARCHAR(500) NULL;