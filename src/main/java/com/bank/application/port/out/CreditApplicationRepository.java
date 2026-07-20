package com.bank.application.port.out;

import com.bank.domain.CreditApplicationStatus;
import com.bank.domain.model.CreditApplication;

import java.util.List;
import java.util.Optional;

/** Output port for credit-application persistence. */
public interface CreditApplicationRepository {

    CreditApplication save(CreditApplication application);

    Optional<CreditApplication> findById(Long id);

    /** A customer's own applications, newest first. */
    List<CreditApplication> findByCustomerId(Long customerId);

    /** Applications assigned to a banker in a given status (e.g. their SUBMITTED queue), newest first. */
    List<CreditApplication> findByBankerIdAndStatus(Long bankerId, CreditApplicationStatus status);

    /** Every application, newest first (admin view). */
    List<CreditApplication> findAll();
}
