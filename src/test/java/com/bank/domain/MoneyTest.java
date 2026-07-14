package com.bank.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.assertj.core.api.Assertions.assertThat;

class MoneyTest {

    @Test
    void normalizeForcesScaleOfTwo() {
        assertThat(Money.normalize(new BigDecimal("10"))).isEqualTo(new BigDecimal("10.00"));
        assertThat(Money.normalize(new BigDecimal("10.5"))).isEqualTo(new BigDecimal("10.50"));
    }

    @Test
    void normalizeUsesBankersRounding() {
        // HALF_EVEN: 2.125 -> 2.12 (down to even), 2.135 -> 2.14 (up to even).
        assertThat(Money.ROUNDING).isEqualTo(RoundingMode.HALF_EVEN);
        assertThat(Money.normalize(new BigDecimal("2.125"))).isEqualTo(new BigDecimal("2.12"));
        assertThat(Money.normalize(new BigDecimal("2.135"))).isEqualTo(new BigDecimal("2.14"));
    }

    @Test
    void isPositiveRejectsZeroAndNegative() {
        assertThat(Money.isPositive(new BigDecimal("0.01"))).isTrue();
        assertThat(Money.isPositive(BigDecimal.ZERO)).isFalse();
        assertThat(Money.isPositive(new BigDecimal("-1"))).isFalse();
        assertThat(Money.isPositive(null)).isFalse();
    }
}
