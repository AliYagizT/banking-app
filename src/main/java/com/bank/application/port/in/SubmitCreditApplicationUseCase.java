package com.bank.application.port.in;

import com.bank.domain.model.CreditApplication;

/** Submit a new credit application; routes it to the customer's assigned banker. */
public interface SubmitCreditApplicationUseCase {

    CreditApplication submit(SubmitCreditApplicationCommand command);
}
