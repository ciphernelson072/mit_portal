ALTER TABLE payment_refunds
  ALTER COLUMN status SET DEFAULT 'PENDING';

ALTER TABLE payment_refunds
  ADD COLUMN reviewed_by VARCHAR(255) NULL,
  ADD COLUMN reviewed_at DATETIME NULL,
  ADD COLUMN review_note VARCHAR(1000) NULL,
  ADD COLUMN refund_reference VARCHAR(120) NULL;
