package com.bank.application.service;

import com.bank.application.port.out.AccountRepository;
import com.bank.domain.exception.NotFoundException;
import org.springframework.stereotype.Component;

/**
 * Authorizes that the authenticated customer may act on a given account.
 *
 * <p>Security decision: when the account is not owned by the caller we respond with
 * "not found" (404) rather than "forbidden" (403). This deliberately does not reveal
 * whether someone else's account id exists, preventing account-enumeration probing.
 */
@Component
public class AccountAccessGuard {

    private final AccountRepository accountRepository;

    public AccountAccessGuard(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    /** Throws {@link NotFoundException} unless {@code accountId} is owned by {@code customerId}. */
    public void requireOwnership(Long accountId, Long customerId) {
        if (!accountRepository.existsByIdAndCustomerId(accountId, customerId)) {
            throw NotFoundException.account(accountId);
        }
    }
}
