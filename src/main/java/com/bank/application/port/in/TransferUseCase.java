package com.bank.application.port.in;

import com.bank.application.model.MoneyMovementResult;

import java.math.BigDecimal;

/** Move money from one customer account to another atomically. */
public interface TransferUseCase {

    MoneyMovementResult transfer(Long sourceAccountId,
                                 Long destinationAccountId,
                                 BigDecimal amount,
                                 String idempotencyKey);
}
