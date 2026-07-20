package com.bank.adapter.out.persistence;

import com.bank.application.port.out.CreditProductRepository;
import com.bank.domain.model.CreditProduct;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/** Adapts {@link CreditProductJpaRepository} to the {@link CreditProductRepository} port. */
@Component
public class CreditProductRepositoryAdapter implements CreditProductRepository {

    private final CreditProductJpaRepository jpa;

    public CreditProductRepositoryAdapter(CreditProductJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<CreditProduct> findByCode(String code) {
        return jpa.findById(code);
    }

    @Override
    public List<CreditProduct> findAll() {
        return jpa.findAll();
    }
}
