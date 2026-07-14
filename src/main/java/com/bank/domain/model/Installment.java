package com.bank.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One row of an amortization (repayment) schedule.
 *
 * @param number            1-based installment number
 * @param dueDate           when this installment is due
 * @param totalPayment      the (equal) monthly payment for this installment
 * @param principalPortion  part of the payment that reduces the principal
 * @param interestPortion   part of the payment that is interest
 * @param remainingPrincipal principal still owed after this installment (0 on the last)
 */
public record Installment(
        int number,
        LocalDate dueDate,
        BigDecimal totalPayment,
        BigDecimal principalPortion,
        BigDecimal interestPortion,
        BigDecimal remainingPrincipal) {
}
