package com.bank.domain.exception;

/**
 * Signals that a write lost an optimistic-locking race on an account row. It is a
 * domain-level abstraction over the persistence provider's optimistic-lock failure,
 * so the application core never depends on Spring/Hibernate exception types. The
 * money-movement service catches it to retry on a fresh transaction; if retries are
 * exhausted it surfaces to the web layer as HTTP 409.
 */
public class ConcurrencyConflictException extends BankingException {

    public ConcurrencyConflictException(String message) {
        super(message);
    }
}
