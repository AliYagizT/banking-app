package com.bank.domain.exception;

/**
 * Base type for expected, domain-level failures (bad input, business-rule
 * violations). These map to 4xx responses by the global handler in the web layer.
 * Unexpected/infrastructure failures should remain as their original exceptions.
 */
public abstract class BankingException extends RuntimeException {

    protected BankingException(String message) {
        super(message);
    }
}
