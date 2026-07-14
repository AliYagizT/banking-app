package com.bank.application.port.out;

import java.util.function.Supplier;

/**
 * Output port abstracting transaction boundaries, so the application core does not
 * depend on Spring's transaction API. The infrastructure implementation also
 * translates persistence-provider failures (optimistic-lock, unique-constraint) into
 * domain exceptions, keeping Spring/Hibernate types out of the core.
 */
public interface TransactionRunner {

    /**
     * Run {@code work} in a brand-new transaction (a fresh one per call — required so
     * the retry loop can start over after a rolled-back attempt).
     */
    <T> T inNewTransaction(Supplier<T> work);

    /** Run {@code work} in a read-only transaction. */
    <T> T inReadOnlyTransaction(Supplier<T> work);
}
