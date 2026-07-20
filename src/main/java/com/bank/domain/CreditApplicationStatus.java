package com.bank.domain;

/** Lifecycle of a credit application. APPROVED and REJECTED are terminal. */
public enum CreditApplicationStatus {
    /** Submitted by the customer, awaiting the assigned banker's decision. */
    SUBMITTED,
    /** Approved by the banker; funds have been disbursed to the customer's account. */
    APPROVED,
    /** Rejected by the banker; no money moved. */
    REJECTED
}
