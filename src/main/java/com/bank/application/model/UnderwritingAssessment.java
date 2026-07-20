package com.bank.application.model;

import java.math.BigDecimal;

/**
 * Decision-support metrics shown on the banker's screen for a credit application. These
 * are advisory: the banker makes the final call. {@code meetsAllCriteria} is true only
 * when every rule below passes, offering a quick "green light" summary.
 *
 * @param monthlyInstallment      computed equal monthly payment (annuity)
 * @param installmentToIncome     installment / declared monthly income (fraction)
 * @param maxInstallmentToIncome  configured ceiling (e.g. 0.50)
 * @param withinIncomeLimit       installmentToIncome &le; maxInstallmentToIncome
 * @param employmentMonths        declared months at current job
 * @param minEmploymentMonths     configured minimum
 * @param meetsEmploymentMinimum  employmentMonths &ge; minEmploymentMonths
 * @param withinProductLimits     amount within the product's min/max
 * @param customerActive          the customer account is active
 * @param totalPayment            installment × term
 * @param totalInterest           totalPayment − principal
 * @param meetsAllCriteria        AND of all the boolean checks above
 */
public record UnderwritingAssessment(
        BigDecimal monthlyInstallment,
        BigDecimal installmentToIncome,
        BigDecimal maxInstallmentToIncome,
        boolean withinIncomeLimit,
        int employmentMonths,
        int minEmploymentMonths,
        boolean meetsEmploymentMinimum,
        boolean withinProductLimits,
        boolean customerActive,
        BigDecimal totalPayment,
        BigDecimal totalInterest,
        boolean meetsAllCriteria) {
}
