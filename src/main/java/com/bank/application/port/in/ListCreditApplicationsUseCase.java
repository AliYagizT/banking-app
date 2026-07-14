package com.bank.application.port.in;

import com.bank.domain.model.CreditApplication;

import java.util.List;

/** List a customer's own credit applications. */
public interface ListCreditApplicationsUseCase {

    List<CreditApplication> listForCustomer(Long customerId);
}
