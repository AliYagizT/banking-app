package com.bank.application.port.in;

import com.bank.application.model.MoneyMovementResult;

import java.math.BigDecimal;

/** Credit money into a customer account from the outside world. */
public interface DepositUseCase {

    MoneyMovementResult deposit(Long accountId, BigDecimal amount, String idempotencyKey);
}
