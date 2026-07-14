package com.bank.adapter.in.web.dto;

import com.bank.domain.model.CreditProduct;

import java.math.BigDecimal;

/** A selectable credit product as returned to customers. */
public record CreditProductResponse(
        String code,
        String name,
        BigDecimal annualInterestRate,
        BigDecimal minAmount,
        BigDecimal maxAmount,
        int maxTermMonths) {

    public static CreditProductResponse from(CreditProduct p) {
        return new CreditProductResponse(
                p.getCode(),
                p.getName(),
                p.getAnnualInterestRate(),
                p.getMinAmount(),
                p.getMaxAmount(),
                p.getMaxTermMonths());
    }
}
