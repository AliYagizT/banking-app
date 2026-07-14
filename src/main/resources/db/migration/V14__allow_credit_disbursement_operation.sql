-- Credit disbursement is a new money-movement operation type. Widen the operation_type
-- check constraints on the ledger and the operation log to accept it, so approving a
-- credit can post its double-entry legs and audit row.
ALTER TABLE ledger_entry
    DROP CONSTRAINT chk_ledger_operation_type;
ALTER TABLE ledger_entry
    ADD CONSTRAINT chk_ledger_operation_type
        CHECK (operation_type IN ('DEPOSIT', 'WITHDRAWAL', 'TRANSFER', 'CREDIT_DISBURSEMENT'));

ALTER TABLE operation_log
    DROP CONSTRAINT chk_operation_log_type;
ALTER TABLE operation_log
    ADD CONSTRAINT chk_operation_log_type
        CHECK (operation_type IN ('DEPOSIT', 'WITHDRAWAL', 'TRANSFER', 'CREDIT_DISBURSEMENT'));

ALTER TABLE idempotency_key
    DROP CONSTRAINT chk_idempotency_operation_type;
ALTER TABLE idempotency_key
    ADD CONSTRAINT chk_idempotency_operation_type
        CHECK (operation_type IN ('DEPOSIT', 'WITHDRAWAL', 'TRANSFER', 'CREDIT_DISBURSEMENT'));
