package com.bank.application.port.in;

import com.bank.domain.model.Account;

/** Open a new account for a customer. */
public interface OpenAccountUseCase {

    Account openAccount(Long customerId, String currency);
}
