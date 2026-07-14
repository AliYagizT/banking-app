package com.bank.domain;

public enum AccountType {
    /** A normal customer-owned account; its balance must never go negative. */
    CUSTOMER,
    /** A bank-internal account (e.g. EXTERNAL-CASH) used as the counter-leg for
     *  deposits and withdrawals so every operation is balanced double-entry. */
    SYSTEM
}
