-- The double-entry ledger. Rows here are IMMUTABLE: never UPDATEd or DELETEd
-- after insert. Corrections are made by appending new compensating entries.
--
-- Sign convention (bank-statement perspective):
--   CREDIT increases the account's balance (money in),
--   DEBIT  decreases the account's balance (money out).
-- Every money movement writes >= 2 entries whose debits and credits net to zero.
--
-- balance_after captures the account balance immediately after this entry was
-- applied, for fast statement rendering and auditing. It is derived data; the
-- authoritative balance is SUM(credits) - SUM(debits) over the account.
CREATE TABLE ledger_entry (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id     BIGINT        NOT NULL REFERENCES account (id),
    operation_id   UUID          NOT NULL,
    operation_type VARCHAR(20)   NOT NULL,
    direction      VARCHAR(6)    NOT NULL,
    amount         NUMERIC(19, 2) NOT NULL,
    balance_after  NUMERIC(19, 2) NOT NULL,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT chk_ledger_direction CHECK (direction IN ('DEBIT', 'CREDIT')),
    CONSTRAINT chk_ledger_operation_type CHECK (operation_type IN ('DEPOSIT', 'WITHDRAWAL', 'TRANSFER')),
    -- Amounts are always stored as a positive magnitude; direction carries the sign.
    CONSTRAINT chk_ledger_amount_positive CHECK (amount > 0)
);

-- Statement queries: entries for one account, newest first.
CREATE INDEX idx_ledger_account_created ON ledger_entry (account_id, created_at DESC, id DESC);
-- Fetch both legs of a single operation.
CREATE INDEX idx_ledger_operation ON ledger_entry (operation_id);
