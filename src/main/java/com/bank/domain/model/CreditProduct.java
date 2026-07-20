package com.bank.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * A selectable credit product (loan type), e.g. a general-purpose or vehicle loan.
 * Carries the annual interest rate and the amount/term limits that constrain an
 * application. Products are reference data seeded by migration; {@code code} is the
 * stable natural key clients refer to.
 */
@Entity
@Table(name = "credit_product")
public class CreditProduct {

    @Id
    @Column(name = "code", nullable = false, length = 30)
    private String code;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    /** Annual nominal interest rate as a fraction (e.g. 0.360000 for 36%). */
    @Column(name = "annual_interest_rate", nullable = false, precision = 9, scale = 6)
    private BigDecimal annualInterestRate;

    @Column(name = "min_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal minAmount;

    @Column(name = "max_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal maxAmount;

    @Column(name = "max_term_months", nullable = false)
    private int maxTermMonths;

    protected CreditProduct() {
        // Required by JPA.
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getAnnualInterestRate() {
        return annualInterestRate;
    }

    public BigDecimal getMinAmount() {
        return minAmount;
    }

    public BigDecimal getMaxAmount() {
        return maxAmount;
    }

    public int getMaxTermMonths() {
        return maxTermMonths;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CreditProduct that)) {
            return false;
        }
        return code != null && code.equals(that.code);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(code);
    }
}
