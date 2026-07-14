package com.bank.application.port.in;

import com.bank.domain.model.Customer;

/** Register a new customer with login credentials. */
public interface RegisterCustomerUseCase {

    Customer register(String fullName, String email, String rawPassword);
}
