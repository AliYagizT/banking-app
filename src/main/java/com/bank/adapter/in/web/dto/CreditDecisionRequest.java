package com.bank.adapter.in.web.dto;

import jakarta.validation.constraints.Size;

/** Optional banker note attached to an approve/reject decision. */
public record CreditDecisionRequest(

        @Size(max = 500)
        String reason) {
}
