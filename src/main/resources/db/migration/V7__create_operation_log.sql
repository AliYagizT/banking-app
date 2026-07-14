-- Dedicated audit / operation log: one IMMUTABLE row per executed money movement.
--
-- This complements the double-entry ledger. The ledger is account-centric (two legs
-- per operation, one row per affected account) and is the source of truth for
-- balances. This log is OPERATION-centric: a single business-level record of each
-- deposit/withdraw/transfer, capturing the idempotency key and the accounts involved,
-- for support and audit queries. Like the ledger, rows are never updated or deleted.
--
-- For a deposit/withdrawal the counter account is the system EXTERNAL-CASH account;
-- for a transfer it is the destination account.
CREATE TABLE operation_log (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    operation_id       UUID          NOT NULL,
    operation_type     VARCHAR(20)   NOT NULL,
    idempotency_key    VARCHAR(80)   NOT NULL,
    primary_account_id BIGINT        NOT NULL REFERENCES account (id),
    counter_account_id BIGINT        REFERENCES account (id),
    amount             NUMERIC(19, 2) NOT NULL,
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT chk_operation_log_type CHECK (operation_type IN ('DEPOSIT', 'WITHDRAWAL', 'TRANSFER')),
    CONSTRAINT chk_operation_log_amount_positive CHECK (amount > 0),
    -- One executed operation produces exactly one audit row; a replayed idempotency
    -- key does not move money again and so must not append a second row.
    CONSTRAINT uq_operation_log_operation UNIQUE (operation_id)
);

-- Audit lookups: operations touching one account, newest first.
CREATE INDEX idx_operation_log_primary_account ON operation_log (primary_account_id, created_at DESC, id DESC);
