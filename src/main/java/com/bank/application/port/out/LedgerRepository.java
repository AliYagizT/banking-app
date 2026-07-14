package com.bank.application.port.out;

import com.bank.application.model.Page;
import com.bank.application.model.PageQuery;
import com.bank.domain.model.LedgerEntry;

import java.math.BigDecimal;
import java.util.List;

/** Output port for the immutable ledger (implemented by a persistence adapter). */
public interface LedgerRepository {

    LedgerEntry save(LedgerEntry entry);

    /** An account's entries, newest first. */
    List<LedgerEntry> findByAccountNewestFirst(Long accountId);

    /** A page of an account's entries, newest first (ordering owned by the adapter). */
    Page<LedgerEntry> findByAccount(Long accountId, PageQuery query);

    /** Authoritative signed balance from the ledger: SUM(credits) − SUM(debits). */
    BigDecimal sumSignedAmount(Long accountId);
}
