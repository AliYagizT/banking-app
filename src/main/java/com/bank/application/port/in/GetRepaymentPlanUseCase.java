package com.bank.application.port.in;

import com.bank.domain.model.RepaymentPlan;

/** Produce the amortization (repayment) schedule for a customer's credit application. */
public interface GetRepaymentPlanUseCase {

    RepaymentPlan repaymentPlanForCustomer(Long applicationId, Long customerId);
}
