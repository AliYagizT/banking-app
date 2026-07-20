package com.bank.domain.model;

import com.bank.domain.Money;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * An equal-installment (annuity) repayment plan — the standard method Turkish banks use
 * for consumer loans. Every monthly payment is the same fixed amount; within it the
 * interest share is high early and the principal share grows over time.
 *
 * <p>The fixed monthly payment is:
 * <pre>
 *   T = A · r·(1+r)^n / ((1+r)^n − 1)      (r &gt; 0)
 *   T = A / n                              (r = 0, interest-free)
 * </pre>
 * where {@code A} is the principal, {@code r} the monthly interest rate, and {@code n}
 * the term in months. All customer-facing amounts are {@link Money}-scaled (2 dp); the
 * annuity itself is computed with extra precision and the final installment absorbs any
 * rounding remainder so the schedule closes exactly at zero.
 */
public record RepaymentPlan(
        BigDecimal amount,
        BigDecimal annualInterestRate,
        int termMonths,
        BigDecimal monthlyInstallment,
        BigDecimal totalPayment,
        BigDecimal totalInterest,
        List<Installment> installments) {

    /** Working precision for the annuity computation before money-rounding each result. */
    private static final MathContext MC = new MathContext(20, RoundingMode.HALF_EVEN);
    private static final BigDecimal MONTHS_PER_YEAR = BigDecimal.valueOf(12);

    /**
     * Build the full amortization schedule.
     *
     * @param amount             principal (loan amount), must be &gt; 0
     * @param annualInterestRate annual nominal rate as a fraction (e.g. 0.36 for 36%), &ge; 0
     * @param termMonths         number of monthly installments, &ge; 1
     * @param firstDueDate       due date of the first installment
     */
    public static RepaymentPlan generate(BigDecimal amount,
                                         BigDecimal annualInterestRate,
                                         int termMonths,
                                         LocalDate firstDueDate) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be > 0");
        }
        if (annualInterestRate == null || annualInterestRate.signum() < 0) {
            throw new IllegalArgumentException("annualInterestRate must be >= 0");
        }
        if (termMonths < 1) {
            throw new IllegalArgumentException("termMonths must be >= 1");
        }

        BigDecimal principal = Money.normalize(amount);
        BigDecimal monthlyRate = annualInterestRate.divide(MONTHS_PER_YEAR, MC);
        BigDecimal installment = monthlyPayment(principal, monthlyRate, termMonths);

        List<Installment> rows = new ArrayList<>(termMonths);
        BigDecimal remaining = principal;
        BigDecimal totalPaid = BigDecimal.ZERO;
        BigDecimal totalInterest = BigDecimal.ZERO;

        for (int i = 1; i <= termMonths; i++) {
            boolean last = i == termMonths;
            BigDecimal interestPortion = Money.normalize(remaining.multiply(monthlyRate, MC));
            BigDecimal payment;
            BigDecimal principalPortion;
            if (last) {
                // The final installment clears whatever principal is left plus its interest,
                // absorbing all accumulated rounding so the balance ends exactly at zero.
                principalPortion = remaining;
                payment = Money.normalize(principalPortion.add(interestPortion));
            } else {
                payment = installment;
                principalPortion = Money.normalize(payment.subtract(interestPortion));
            }
            remaining = Money.normalize(remaining.subtract(principalPortion));
            totalPaid = totalPaid.add(payment);
            totalInterest = totalInterest.add(interestPortion);
            rows.add(new Installment(i, firstDueDate.plusMonths(i - 1L),
                    payment, principalPortion, interestPortion, remaining.max(Money.ZERO)));
        }

        return new RepaymentPlan(principal, annualInterestRate, termMonths, installment,
                Money.normalize(totalPaid), Money.normalize(totalInterest), List.copyOf(rows));
    }

    /**
     * The fixed monthly payment for an annuity loan, money-rounded (2 dp). Exposed so
     * underwriting can show the installment/income ratio before any schedule is built.
     */
    public static BigDecimal monthlyPayment(BigDecimal amount, BigDecimal monthlyRate, int termMonths) {
        if (termMonths < 1) {
            throw new IllegalArgumentException("termMonths must be >= 1");
        }
        BigDecimal principal = Money.normalize(amount);
        if (monthlyRate == null || monthlyRate.signum() == 0) {
            return Money.normalize(principal.divide(BigDecimal.valueOf(termMonths), MC));
        }
        // factor = (1+r)^n
        BigDecimal factor = BigDecimal.ONE.add(monthlyRate).pow(termMonths, MC);
        BigDecimal numerator = principal.multiply(monthlyRate, MC).multiply(factor, MC);
        BigDecimal denominator = factor.subtract(BigDecimal.ONE);
        return Money.normalize(numerator.divide(denominator, MC));
    }

    /** Convenience: fixed monthly payment from an annual rate (fraction) and term. */
    public static BigDecimal monthlyPaymentFromAnnual(BigDecimal amount,
                                                      BigDecimal annualInterestRate,
                                                      int termMonths) {
        BigDecimal monthlyRate = annualInterestRate == null
                ? BigDecimal.ZERO
                : annualInterestRate.divide(MONTHS_PER_YEAR, MC);
        return monthlyPayment(amount, monthlyRate, termMonths);
    }
}
