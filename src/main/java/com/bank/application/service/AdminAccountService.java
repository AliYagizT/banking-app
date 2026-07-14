package com.bank.application.service;

import com.bank.application.port.in.AdminAccountUseCase;
import com.bank.application.port.out.AccountRepository;
import com.bank.application.port.out.TransactionRunner;
import com.bank.domain.AccountStatus;
import com.bank.domain.exception.NotFoundException;
import com.bank.domain.exception.ValidationException;
import com.bank.domain.model.Account;
import org.springframework.stereotype.Service;

/**
 * Administrative account operations. Access is restricted to the ADMIN role at the web
 * layer; this service assumes the caller is already authorized.
 */
@Service
public class AdminAccountService implements AdminAccountUseCase {

    private final AccountRepository accountRepository;
    private final TransactionRunner transactionRunner;

    public AdminAccountService(AccountRepository accountRepository, TransactionRunner transactionRunner) {
        this.accountRepository = accountRepository;
        this.transactionRunner = transactionRunner;
    }

    @Override
    public Account view(Long accountId) {
        return transactionRunner.inReadOnlyTransaction(() -> accountRepository.findById(accountId)
                .orElseThrow(() -> NotFoundException.account(accountId)));
    }

    @Override
    public Account freeze(Long accountId) {
        return changeStatus(accountId, AccountStatus.FROZEN);
    }

    @Override
    public Account close(Long accountId) {
        return changeStatus(accountId, AccountStatus.CLOSED);
    }

    private Account changeStatus(Long accountId, AccountStatus status) {
        return transactionRunner.inNewTransaction(() -> {
            Account account = accountRepository.findById(accountId)
                    .orElseThrow(() -> NotFoundException.account(accountId));
            // The internal system account is not administrable.
            if (account.isSystemAccount()) {
                throw new ValidationException("System accounts cannot be modified");
            }
            account.setStatus(status);
            return accountRepository.save(account);
        });
    }
}
