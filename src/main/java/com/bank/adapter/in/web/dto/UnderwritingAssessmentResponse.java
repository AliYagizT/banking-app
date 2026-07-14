package com.bank.adapter.in.web.dto;

import com.bank.application.model.UnderwritingAssessment;

import java.math.BigDecimal;

/** Decision-support metrics shown on the banker's screen (advisory). */
public record UnderwritingAssessmentResponse(
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

    public static UnderwritingAssessmentResponse from(UnderwritingAssessment a) {
        return new UnderwritingAssessmentResponse(
                a.monthlyInstallment(),
                a.installmentToIncome(),
                a.maxInstallmentToIncome(),
                a.withinIncomeLimit(),
                a.employmentMonths(),
                a.minEmploymentMonths(),
                a.meetsEmploymentMinimum(),
                a.withinProductLimits(),
                a.customerActive(),
                a.totalPayment(),
                a.totalInterest(),
                a.meetsAllCriteria());
    }
}
