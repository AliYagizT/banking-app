package com.bank.domain.exception;

/**
 * Signals that a competing transaction committed a row with the same idempotency key
 * first (a unique-constraint collision). A domain-level abstraction over the
 * persistence provider's data-integrity violation, so the application core stays free
 * of Spring/Hibernate types. The money-movement service catches it and returns the
 * winner's stored result instead of moving money twice.
 */
public class DuplicateIdempotencyKeyException extends BankingException {

    public DuplicateIdempotencyKeyException(String message) {
        super(message);
    }
}
