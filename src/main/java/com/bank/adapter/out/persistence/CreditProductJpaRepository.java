package com.bank.adapter.out.persistence;

import com.bank.domain.model.CreditProduct;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data JPA repository for credit products (an outbound-adapter detail). */
public interface CreditProductJpaRepository extends JpaRepository<CreditProduct, String> {
}
