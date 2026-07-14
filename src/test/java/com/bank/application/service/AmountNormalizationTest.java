package com.bank.application.service;

import com.bank.domain.exception.ValidationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Pure unit tests for the amount-validation rules (no Spring context). */
class AmountNormalizationTest {

    @Test
    void normalizesToScaleTwo() {
        assertThat(MoneyMovementService.normalizeAmount(new BigDecimal("5")))
                .isEqualTo(new BigDecimal("5.00"));
        assertThat(MoneyMovementService.normalizeAmount(new BigDecimal("5.1")))
                .isEqualTo(new BigDecimal("5.10"));
        // Trailing zeros beyond scale 2 are fine (they are not real precision).
        assertThat(MoneyMovementService.normalizeAmount(new BigDecimal("5.100")))
                .isEqualTo(new BigDecimal("5.10"));
    }

    @Test
    void rejectsNull() {
        assertThatThrownBy(() -> MoneyMovementService.normalizeAmount(null))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void rejectsZeroAndNegative() {
        assertThatThrownBy(() -> MoneyMovementService.normalizeAmount(BigDecimal.ZERO))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("greater than zero");
        assertThatThrownBy(() -> MoneyMovementService.normalizeAmount(new BigDecimal("-0.01")))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void rejectsMoreThanTwoDecimalPlaces() {
        assertThatThrownBy(() -> MoneyMovementService.normalizeAmount(new BigDecimal("1.001")))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("decimal places");
    }
}
