package com.bank.adapter.out.persistence;

import com.bank.domain.model.LedgerEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

/** Spring Data JPA repository for the ledger (an outbound-adapter detail). */
public interface LedgerEntryJpaRepository extends JpaRepository<LedgerEntry, Long> {

    List<LedgerEntry> findByAccountIdOrderByCreatedAtDescIdDesc(Long accountId);

    Page<LedgerEntry> findByAccountId(Long accountId, Pageable pageable);

    /**
     * The authoritative balance derived straight from the ledger: SUM of credits
     * minus SUM of debits. Used to verify the cached {@code account.balance} never
     * drifts from the source of truth.
     */
    @Query("""
            select coalesce(sum(case when entry.direction = com.bank.domain.LedgerDirection.CREDIT
                                     then entry.amount else entry.amount * -1 end), 0)
            from LedgerEntry entry
            where entry.account.id = :accountId
            """)
    BigDecimal sumSignedAmountByAccountId(@Param("accountId") Long accountId);
}
