-- The EXTERNAL-CASH system account represents money entering/leaving the bank
-- from the outside world. Deposits credit a customer account and debit this one;
-- withdrawals do the reverse. This keeps every operation balanced double-entry.
-- Its balance is allowed to go negative (it mirrors total customer deposits).
INSERT INTO account (account_number, customer_id, account_type, currency, status, balance)
VALUES ('EXTERNAL-CASH', NULL, 'SYSTEM', 'USD', 'ACTIVE', 0);
