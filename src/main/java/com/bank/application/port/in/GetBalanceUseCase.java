package com.bank.application.port.in;

import com.bank.application.model.BalanceView;

/** Read an account's current balance. */
public interface GetBalanceUseCase {

    BalanceView getBalance(Long accountId);
}
