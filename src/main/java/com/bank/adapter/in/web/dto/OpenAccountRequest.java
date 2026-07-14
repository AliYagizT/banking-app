package com.bank.adapter.in.web.dto;

import jakarta.validation.constraints.Size;

/**
 * Opens an account for the <i>authenticated</i> customer; the owner is taken from the
 * security context, never from the request, so a customer cannot open accounts for others.
 */
public record OpenAccountRequest(

        /** Optional ISO currency code; defaults to USD when omitted. */
        @Size(min = 3, max = 3)
        String currency) {
}
