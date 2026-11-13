ALTER TABLE payment_refunds
  ADD CONSTRAINT uk_payment_refund_reference UNIQUE (refund_reference);
