package com.bank.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body for creating a customer profile. The email is taken from the verified bearer token
 * (never the body) and credentials live in Firebase, so only the display name is supplied.
 */
public record CreateCustomerRequest(

        @NotBlank
        @Size(max = 200)
        String fullName) {
}
