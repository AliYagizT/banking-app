package com.bank.application.port.in;

import com.bank.domain.model.Customer;

/** Fetch a customer by id. */
public interface GetCustomerUseCase {

    Customer getCustomer(Long customerId);
}
