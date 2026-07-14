package com.bank.domain.exception;

import com.bank.domain.AccountStatus;

/** A money movement targeted an account that is not in the ACTIVE state. */
public class AccountNotActiveException extends BankingException {

    public AccountNotActiveException(Long accountId, AccountStatus status) {
        super("Account " + accountId + " is not active (status: " + status + ")");
    }
}
