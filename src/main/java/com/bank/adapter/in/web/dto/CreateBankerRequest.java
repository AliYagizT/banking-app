package com.bank.adapter.in.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Admin form to add a banker: creates a Firebase login + a BANKER customer row. */
public record CreateBankerRequest(

        @NotBlank
        @Size(max = 200)
        String fullName,

        @NotBlank
        @Email
        @Size(max = 320)
        String email,

        // Firebase requires at least 6 characters.
        @NotBlank
        @Size(min = 6, max = 72)
        String password) {
}
