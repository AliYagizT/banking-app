package com.bank.domain;

/**
 * Direction of a ledger entry from the perspective of the account it belongs to.
 * CREDIT increases the account balance (money in); DEBIT decreases it (money out).
 */
public enum LedgerDirection {
    DEBIT,
    CREDIT
}
