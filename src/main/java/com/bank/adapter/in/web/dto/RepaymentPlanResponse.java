package com.bank.adapter.in.web.dto;

import com.bank.domain.model.Installment;
import com.bank.domain.model.RepaymentPlan;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** The amortization schedule (repayment terms) a customer sees for their credit. */
public record RepaymentPlanResponse(
        BigDecimal amount,
        BigDecimal annualInterestRate,
        int termMonths,
        BigDecimal monthlyInstallment,
        BigDecimal totalPayment,
        BigDecimal totalInterest,
        List<InstallmentResponse> installments) {

    public static RepaymentPlanResponse from(RepaymentPlan plan) {
        List<InstallmentResponse> rows = plan.installments().stream()
                .map(InstallmentResponse::from)
                .toList();
        return new RepaymentPlanResponse(
                plan.amount(),
                plan.annualInterestRate(),
                plan.termMonths(),
                plan.monthlyInstallment(),
                plan.totalPayment(),
                plan.totalInterest(),
                rows);
    }

    /** One installment row. */
    public record InstallmentResponse(
            int number,
            LocalDate dueDate,
            BigDecimal totalPayment,
            BigDecimal principalPortion,
            BigDecimal interestPortion,
            BigDecimal remainingPrincipal) {

        public static InstallmentResponse from(Installment i) {
            return new InstallmentResponse(
                    i.number(),
                    i.dueDate(),
                    i.totalPayment(),
                    i.principalPortion(),
                    i.interestPortion(),
                    i.remainingPrincipal());
        }
    }
}
