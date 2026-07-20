package com.bank.adapter.out.persistence;

import com.bank.domain.CreditApplicationStatus;
import com.bank.domain.model.CreditApplication;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Spring Data JPA repository for credit applications (an outbound-adapter detail). */
public interface CreditApplicationJpaRepository extends JpaRepository<CreditApplication, Long> {

    List<CreditApplication> findByCustomerIdOrderByCreatedAtDesc(Long customerId);

    List<CreditApplication> findByBankerIdAndStatusOrderByCreatedAtDesc(
            Long bankerId, CreditApplicationStatus status);

    List<CreditApplication> findAllByOrderByCreatedAtDesc();
}
