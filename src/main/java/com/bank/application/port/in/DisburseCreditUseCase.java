package com.bank.application.port.in;

import com.bank.application.model.MoneyMovementResult;

import java.math.BigDecimal;

/**
 * Credits approved credit funds into a customer account. Like a deposit, the money
 * enters from the external cash counter-leg, but it is recorded as a
 * {@code CREDIT_DISBURSEMENT} so the customer sees it distinctly in their statement.
 */
public interface DisburseCreditUseCase {

    MoneyMovementResult disburse(Long accountId, BigDecimal amount, String idempotencyKey);
}
