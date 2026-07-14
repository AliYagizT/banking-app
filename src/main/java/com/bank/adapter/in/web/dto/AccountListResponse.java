package com.bank.adapter.in.web.dto;

import com.bank.domain.model.Account;

import java.util.List;

/**
 * Envelope for "list my accounts". A {@code content} wrapper (rather than a bare array)
 * keeps the response shape stable and consistent with the paginated envelopes.
 */
public record AccountListResponse(List<AccountResponse> content) {

    public static AccountListResponse from(List<Account> accounts) {
        return new AccountListResponse(accounts.stream().map(AccountResponse::from).toList());
    }
}
