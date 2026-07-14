package com.bank.adapter.in.web.dto;

import com.bank.domain.AccountStatus;
import com.bank.domain.model.Account;

import java.math.BigDecimal;
import java.time.Instant;

public record AccountResponse(
        Long id,
        String accountNumber,
        Long customerId,
        String currency,
        AccountStatus status,
        BigDecimal balance,
        Instant createdAt) {

    public static AccountResponse from(Account account) {
        // account.getCustomer() may be a lazy proxy, but reading its id never triggers a load.
        Long customerId = account.getCustomer() == null ? null : account.getCustomer().getId();
        return new AccountResponse(
                account.getId(),
                account.getAccountNumber(),
                customerId,
                account.getCurrency(),
                account.getStatus(),
                account.getBalance(),
                account.getCreatedAt());
    }
}
