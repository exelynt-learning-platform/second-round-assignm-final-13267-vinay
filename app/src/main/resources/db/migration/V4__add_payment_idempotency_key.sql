-- V4: Add idempotency_key column to payments for preventing duplicate payment initiation
ALTER TABLE payments ADD COLUMN idempotency_key VARCHAR(255) NULL;

ALTER TABLE payments ADD CONSTRAINT uc_payments_idempotency_key UNIQUE (idempotency_key);

CREATE INDEX idx_payment_idempotency ON payments(idempotency_key);
