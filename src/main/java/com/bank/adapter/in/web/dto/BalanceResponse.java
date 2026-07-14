package com.bank.adapter.in.web.dto;

import com.bank.application.model.BalanceView;

import java.math.BigDecimal;

public record BalanceResponse(Long accountId, String currency, BigDecimal balance) {

    public static BalanceResponse from(BalanceView view) {
        return new BalanceResponse(view.accountId(), view.currency(), view.balance());
    }
}
