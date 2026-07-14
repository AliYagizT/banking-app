package com.bank.application.port.out;

import com.bank.domain.model.CreditProduct;

import java.util.List;
import java.util.Optional;

/** Output port for credit-product reference data. */
public interface CreditProductRepository {

    Optional<CreditProduct> findByCode(String code);

    List<CreditProduct> findAll();
}
