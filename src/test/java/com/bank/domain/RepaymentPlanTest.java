package com.bank.domain;

import com.bank.domain.model.Installment;
import com.bank.domain.model.RepaymentPlan;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Unit tests for the annuity (equal-installment) repayment schedule. */
class RepaymentPlanTest {

    private static final LocalDate START = LocalDate.of(2026, 1, 15);

    @Test
    void interestFreeLoanSplitsPrincipalEvenly() {
        RepaymentPlan plan = RepaymentPlan.generate(new BigDecimal("1200.00"), BigDecimal.ZERO, 12, START);

        assertThat(plan.monthlyInstallment()).isEqualByComparingTo("100.00");
        assertThat(plan.totalPayment()).isEqualByComparingTo("1200.00");
        assertThat(plan.totalInterest()).isEqualByComparingTo("0.00");
        assertThat(plan.installments()).hasSize(12);
        assertThat(plan.installments().get(11).remainingPrincipal()).isEqualByComparingTo("0.00");
    }

    @Test
    void installmentsAreDueMonthlyStartingAtTheGivenFirstDueDate() {
        RepaymentPlan plan = RepaymentPlan.generate(new BigDecimal("1000.00"), BigDecimal.ZERO, 3, START);

        assertThat(plan.installments().get(0).dueDate()).isEqualTo(LocalDate.of(2026, 1, 15));
        assertThat(plan.installments().get(2).dueDate()).isEqualTo(LocalDate.of(2026, 3, 15));
    }

    @Test
    void annuityInstallmentMatchesTheStandardFormula() {
        // 10,000 principal at 12% annual (1% monthly) over 12 months -> ~888.49 / month.
        RepaymentPlan plan = RepaymentPlan.generate(new BigDecimal("10000.00"), new BigDecimal("0.12"), 12, START);

        assertThat(plan.monthlyInstallment().doubleValue()).isCloseTo(888.49, org.assertj.core.data.Offset.offset(0.05));
        assertThat(RepaymentPlan.monthlyPaymentFromAnnual(new BigDecimal("10000.00"), new BigDecimal("0.12"), 12))
                .isEqualByComparingTo(plan.monthlyInstallment());
    }

    @Test
    void scheduleClosesExactlyToZeroAndSumsAreConsistent() {
        RepaymentPlan plan = RepaymentPlan.generate(new BigDecimal("60000.00"), new BigDecimal("0.36"), 12, START);

        BigDecimal summedPayments = BigDecimal.ZERO;
        BigDecimal summedPrincipal = BigDecimal.ZERO;
        BigDecimal summedInterest = BigDecimal.ZERO;
        for (Installment i : plan.installments()) {
            summedPayments = summedPayments.add(i.totalPayment());
            summedPrincipal = summedPrincipal.add(i.principalPortion());
            summedInterest = summedInterest.add(i.interestPortion());
        }

        // Principal fully repaid, schedule ends at zero, and totals reconcile.
        assertThat(summedPrincipal).isEqualByComparingTo("60000.00");
        assertThat(plan.installments().get(11).remainingPrincipal()).isEqualByComparingTo("0.00");
        assertThat(summedPayments).isEqualByComparingTo(plan.totalPayment());
        assertThat(summedInterest).isEqualByComparingTo(plan.totalInterest());
        assertThat(plan.totalInterest()).isEqualByComparingTo(plan.totalPayment().subtract(new BigDecimal("60000.00")));
        // Early installments are interest-heavy; later ones principal-heavy.
        assertThat(plan.installments().get(0).interestPortion())
                .isGreaterThan(plan.installments().get(11).interestPortion());
    }

    @Test
    void rejectsInvalidInputs() {
        assertThatThrownBy(() -> RepaymentPlan.generate(BigDecimal.ZERO, BigDecimal.ZERO, 12, START))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RepaymentPlan.generate(new BigDecimal("1000"), new BigDecimal("-0.1"), 12, START))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RepaymentPlan.generate(new BigDecimal("1000"), BigDecimal.ZERO, 0, START))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
