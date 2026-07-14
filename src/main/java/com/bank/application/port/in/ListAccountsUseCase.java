package com.bank.application.port.in;

import com.bank.domain.model.Account;

import java.util.List;

/** List all accounts owned by a customer. */
public interface ListAccountsUseCase {

    List<Account> listAccounts(Long customerId);
}
