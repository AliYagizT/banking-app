package com.bank.domain;

public enum AccountStatus {
    /** Fully operational: can send and receive money. */
    ACTIVE,
    /** Temporarily blocked: no money may move in or out. */
    FROZEN,
    /** Permanently closed. */
    CLOSED
}
