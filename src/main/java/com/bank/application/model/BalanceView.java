package com.bank.application.model;

import java.math.BigDecimal;

/** A read-only snapshot of an account's current balance. */
public record BalanceView(Long accountId, String currency, BigDecimal balance) {
}
