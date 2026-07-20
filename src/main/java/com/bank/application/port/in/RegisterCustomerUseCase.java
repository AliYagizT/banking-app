package com.bank.application.port.in;

import com.bank.domain.model.Customer;

/**
 * Create a customer profile for an already-authenticated identity. Credentials live in the
 * external identity provider (Firebase); the email comes from the verified token.
 */
public interface RegisterCustomerUseCase {

    Customer register(String fullName, String email);
}
