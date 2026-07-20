package com.bank.application.port.in;

import com.bank.domain.model.CreditApplication;

/** Fetch a single credit application, authorized for the requesting customer. */
public interface GetCreditApplicationUseCase {

    /** The application, only if owned by {@code customerId}; otherwise not found. */
    CreditApplication getForCustomer(Long applicationId, Long customerId);
}
