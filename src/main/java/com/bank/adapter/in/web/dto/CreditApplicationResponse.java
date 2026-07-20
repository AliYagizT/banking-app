package com.bank.adapter.in.web.dto;

import com.bank.domain.CreditApplicationStatus;
import com.bank.domain.model.CreditApplication;

import java.math.BigDecimal;
import java.time.Instant;

/** A credit application as returned to customers and bankers. */
public record CreditApplicationResponse(
        Long id,
        Long customerId,
        Long bankerId,
        String productCode,
        BigDecimal amount,
        int termMonths,
        BigDecimal annualInterestRate,
        BigDecimal monthlyIncome,
        String profession,
        int employmentMonths,
        Long disbursementAccountId,
        BigDecimal monthlyInstallment,
        CreditApplicationStatus status,
        String decisionReason,
        Instant createdAt,
        Instant decidedAt,
        Instant disbursedAt) {

    public static CreditApplicationResponse from(CreditApplication a) {
        return new CreditApplicationResponse(
                a.getId(),
                a.getCustomerId(),
                a.getBankerId(),
                a.getProductCode(),
                a.getAmount(),
                a.getTermMonths(),
                a.getAnnualInterestRate(),
                a.getMonthlyIncome(),
                a.getProfession(),
                a.getEmploymentMonths(),
                a.getDisbursementAccountId(),
                a.getMonthlyInstallment(),
                a.getStatus(),
                a.getDecisionReason(),
                a.getCreatedAt(),
                a.getDecidedAt(),
                a.getDisbursedAt());
    }
}
