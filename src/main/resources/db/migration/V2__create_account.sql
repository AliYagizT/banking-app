-- Accounts hold a cached `balance` for performance, but the ledger remains the
-- source of truth. `version` backs JPA optimistic locking against lost updates.
--
-- account_type:
--   CUSTOMER - a normal customer-owned account; balance must never go negative.
--   SYSTEM   - bank-internal accounts (e.g. EXTERNAL-CASH) that represent the
--              outside world so deposits/withdrawals remain balanced double-entry;
--              these are exempt from the non-negative balance check.
CREATE TABLE account (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_number VARCHAR(34)   NOT NULL,
    customer_id    BIGINT        REFERENCES customer (id),
    account_type   VARCHAR(20)   NOT NULL DEFAULT 'CUSTOMER',
    currency       VARCHAR(3)    NOT NULL,
    status         VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    balance        NUMERIC(19, 2) NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version        BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uq_account_number UNIQUE (account_number),
    CONSTRAINT chk_account_type CHECK (account_type IN ('CUSTOMER', 'SYSTEM')),
    CONSTRAINT chk_account_status CHECK (status IN ('ACTIVE', 'FROZEN', 'CLOSED')),
    -- A customer account must reference a customer; system accounts need not.
    CONSTRAINT chk_account_owner CHECK (account_type = 'SYSTEM' OR customer_id IS NOT NULL),
    -- Only customer accounts are forbidden from going negative.
    CONSTRAINT chk_account_balance_nonneg CHECK (account_type = 'SYSTEM' OR balance >= 0)
);

CREATE INDEX idx_account_customer ON account (customer_id);
