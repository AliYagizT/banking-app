package com.bank.domain;

/** Role for role-based access control. */
public enum CustomerRole {
    /** A normal customer: may access and operate only on their own accounts. */
    CUSTOMER,
    /** A relationship banker: evaluates credit applications of their assigned customers. */
    BANKER,
    /** A bank operator: may view any account and freeze/close accounts. */
    ADMIN
}
