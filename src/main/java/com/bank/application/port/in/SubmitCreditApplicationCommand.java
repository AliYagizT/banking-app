package com.bank.application.port.in;

import java.math.BigDecimal;

/**
 * A customer's request to open a credit application. {@code customerId} is taken from
 * the authenticated principal (never the request body); the rest is the customer's
 * declaration plus the chosen product and disbursement account.
 */
public record SubmitCreditApplicationCommand(
        Long customerId,
        String productCode,
        BigDecimal amount,
        int termMonths,
        BigDecimal monthlyIncome,
        String profession,
        int employmentMonths,
        Long disbursementAccountId) {
}
