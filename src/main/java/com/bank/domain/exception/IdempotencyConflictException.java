package com.bank.domain.exception;

/**
 * The same idempotency key was replayed with DIFFERENT request parameters. This is
 * a client error: an idempotency key must identify exactly one logical operation.
 */
public class IdempotencyConflictException extends BankingException {

    public IdempotencyConflictException(String idempotencyKey) {
        super("Idempotency key '" + idempotencyKey
                + "' was already used for a different request");
    }
}
