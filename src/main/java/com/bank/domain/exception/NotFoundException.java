package com.bank.domain.exception;

/** A referenced entity (customer, account, ...) does not exist. Maps to HTTP 404. */
public class NotFoundException extends BankingException {

    public NotFoundException(String message) {
        super(message);
    }

    public static NotFoundException customer(Long customerId) {
        return new NotFoundException("Customer not found: " + customerId);
    }

    public static NotFoundException account(Long accountId) {
        return new NotFoundException("Account not found: " + accountId);
    }

    public static NotFoundException creditApplication(Long applicationId) {
        return new NotFoundException("Credit application not found: " + applicationId);
    }

    public static NotFoundException creditProduct(String productCode) {
        return new NotFoundException("Credit product not found: " + productCode);
    }
}
