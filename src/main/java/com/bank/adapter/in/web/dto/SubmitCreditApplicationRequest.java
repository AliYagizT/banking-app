package com.bank.adapter.in.web.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

/**
 * Body for submitting a credit application. The customer id is taken from the
 * authenticated principal, never the body.
 */
public record SubmitCreditApplicationRequest(

        @NotBlank
        String productCode,

        @NotNull
        @Positive
        @Digits(integer = 17, fraction = 2)
        BigDecimal amount,

        @Min(1)
        @Max(600)
        int termMonths,

        @NotNull
        @Positive
        @Digits(integer = 17, fraction = 2)
        BigDecimal monthlyIncome,

        @NotBlank
        String profession,

        @PositiveOrZero
        int employmentMonths,

        @NotNull
        Long disbursementAccountId) {
}
