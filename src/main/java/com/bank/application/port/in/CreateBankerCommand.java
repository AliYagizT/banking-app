package com.bank.application.port.in;

/** An admin's request to add a banker: creates an identity-provider account + a DB row. */
public record CreateBankerCommand(String fullName, String email, String password) {
}
