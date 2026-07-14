package com.bank.domain;

public enum OperationType {
    DEPOSIT,
    WITHDRAWAL,
    TRANSFER,
    /** Money credited to a customer account as the disbursement of an approved credit. */
    CREDIT_DISBURSEMENT
}
