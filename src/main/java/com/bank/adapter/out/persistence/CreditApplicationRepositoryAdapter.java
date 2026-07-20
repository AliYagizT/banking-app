package com.bank.adapter.out.persistence;

import com.bank.application.port.out.CreditApplicationRepository;
import com.bank.domain.CreditApplicationStatus;
import com.bank.domain.model.CreditApplication;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/** Adapts {@link CreditApplicationJpaRepository} to the {@link CreditApplicationRepository} port. */
@Component
public class CreditApplicationRepositoryAdapter implements CreditApplicationRepository {

    private final CreditApplicationJpaRepository jpa;

    public CreditApplicationRepositoryAdapter(CreditApplicationJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public CreditApplication save(CreditApplication application) {
        return jpa.save(application);
    }

    @Override
    public Optional<CreditApplication> findById(Long id) {
        return jpa.findById(id);
    }

    @Override
    public List<CreditApplication> findByCustomerId(Long customerId) {
        return jpa.findByCustomerIdOrderByCreatedAtDesc(customerId);
    }

    @Override
    public List<CreditApplication> findByBankerIdAndStatus(Long bankerId, CreditApplicationStatus status) {
        return jpa.findByBankerIdAndStatusOrderByCreatedAtDesc(bankerId, status);
    }

    @Override
    public List<CreditApplication> findAll() {
        return jpa.findAllByOrderByCreatedAtDesc();
    }
}
