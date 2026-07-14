package com.bank.application.port.in;

import com.bank.domain.model.Account;

/** Fetch an account by id. */
public interface GetAccountUseCase {

    Account getAccount(Long accountId);
}
