package com.bank.domain.exception;

/**
 * A request violates a business rule (duplicate email, unsupported currency,
 * inactive customer, ...). Maps to HTTP 400/409 in the web layer.
 */
public class ValidationException extends BankingException {

    public ValidationException(String message) {
        super(message);
    }
}
