package com.bank.domain.exception;

import java.math.BigDecimal;

/** A debit was rejected because the account does not hold enough money. */
public class InsufficientFundsException extends BankingException {

    public InsufficientFundsException(Long accountId, BigDecimal balance, BigDecimal requested) {
        super("Account " + accountId + " has insufficient funds: balance " + balance
                + " is less than requested " + requested);
    }
}
