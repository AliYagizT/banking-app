package com.bank.adapter.in.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCustomerRequest(

        @NotBlank
        @Size(max = 200)
        String fullName,

        @NotBlank
        @Email
        @Size(max = 320)
        String email,

        // The password the customer will authenticate with (BCrypt input is capped at 72 bytes).
        @NotBlank
        @Size(min = 8, max = 72)
        String password) {
}
