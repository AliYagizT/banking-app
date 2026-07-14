package com.bank.adapter.in.web.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/** Body for a transfer. The idempotency key is carried in a header. */
public record TransferRequest(

        @NotNull
        Long sourceAccountId,

        @NotNull
        Long destinationAccountId,

        @NotNull
        @Positive
        @Digits(integer = 17, fraction = 2)
        BigDecimal amount) {
}
