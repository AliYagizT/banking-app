package com.bank.application.port.in;

import com.bank.application.model.MoneyMovementResult;

import java.math.BigDecimal;

/** Debit money out of a customer account to the outside world. */
public interface WithdrawUseCase {

    MoneyMovementResult withdraw(Long accountId, BigDecimal amount, String idempotencyKey);
}
