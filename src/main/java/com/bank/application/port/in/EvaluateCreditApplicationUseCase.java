package com.bank.application.port.in;

import com.bank.application.model.UnderwritingAssessment;
import com.bank.domain.model.CreditApplication;

import java.util.List;

/**
 * The banker side of credit analysis: review the queue of assigned applications, inspect
 * the underwriting metrics, and decide (approve → disbursement, or reject). Every method
 * authorizes that the application belongs to the acting banker's assigned customers.
 */
public interface EvaluateCreditApplicationUseCase {

    /** The banker's pending queue: SUBMITTED applications of their assigned customers. */
    List<CreditApplication> queueForBanker(Long bankerId);

    /** A single application, only if it belongs to one of the banker's customers. */
    CreditApplication getForBanker(Long applicationId, Long bankerId);

    /** Decision-support metrics for the application (authorized for the banker). */
    UnderwritingAssessment assess(Long applicationId, Long bankerId);

    /** Approve and disburse the funds to the customer's account. Returns the updated application. */
    CreditApplication approve(Long applicationId, Long bankerId, String reason);

    /** Reject the application (no money moves). Returns the updated application. */
    CreditApplication reject(Long applicationId, Long bankerId, String reason);
}
