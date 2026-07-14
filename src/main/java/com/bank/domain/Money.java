package com.bank.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Central policy for monetary amounts.
 *
 * <p>All money in this application is a {@link BigDecimal} with a fixed scale of 2
 * and an explicit {@link RoundingMode}. {@code double}/{@code float} are never used
 * for money — they cannot represent decimal fractions exactly.
 *
 * <p>Rounding is {@link RoundingMode#HALF_EVEN} ("banker's rounding"): it rounds to
 * the nearest neighbour and, on a tie, to the even digit. Over many operations this
 * avoids the systematic upward bias of HALF_UP, which is the conventional choice for
 * financial systems.
 */
public final class Money {

    public static final int SCALE = 2;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_EVEN;
    public static final BigDecimal ZERO = normalize(BigDecimal.ZERO);

    private Money() {
    }

    /** Coerce a value to the canonical money representation (scale 2, banker's rounding). */
    public static BigDecimal normalize(BigDecimal amount) {
        if (amount == null) {
            return null;
        }
        return amount.setScale(SCALE, ROUNDING);
    }

    /** True when the amount is strictly greater than zero (a valid money movement). */
    public static boolean isPositive(BigDecimal amount) {
        return amount != null && amount.signum() > 0;
    }
}
