package com.bank.adapter.out.persistence;

import com.bank.application.model.Page;
import com.bank.application.model.PageQuery;
import com.bank.application.port.out.LedgerRepository;
import com.bank.domain.model.LedgerEntry;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Adapts the {@link LedgerEntryJpaRepository} to the {@link LedgerRepository} output
 * port, translating the framework-neutral {@link PageQuery}/{@link Page} to and from
 * Spring Data's paging types. The deterministic statement ordering lives here because
 * it is a persistence-query detail.
 */
@Component
public class LedgerRepositoryAdapter implements LedgerRepository {

    /** Newest first, with id as a tie-breaker so page boundaries are stable. */
    private static final Sort NEWEST_FIRST =
            Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final LedgerEntryJpaRepository jpa;

    public LedgerRepositoryAdapter(LedgerEntryJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public LedgerEntry save(LedgerEntry entry) {
        return jpa.save(entry);
    }

    @Override
    public List<LedgerEntry> findByAccountNewestFirst(Long accountId) {
        return jpa.findByAccountIdOrderByCreatedAtDescIdDesc(accountId);
    }

    @Override
    public Page<LedgerEntry> findByAccount(Long accountId, PageQuery query) {
        int page = Math.max(query.page(), 0);
        int size = Math.max(query.size(), 1);
        org.springframework.data.domain.Page<LedgerEntry> result =
                jpa.findByAccountId(accountId, PageRequest.of(page, size, NEWEST_FIRST));
        return new Page<>(result.getContent(), result.getNumber(), result.getSize(), result.getTotalElements());
    }

    @Override
    public BigDecimal sumSignedAmount(Long accountId) {
        return jpa.sumSignedAmountByAccountId(accountId);
    }
}
