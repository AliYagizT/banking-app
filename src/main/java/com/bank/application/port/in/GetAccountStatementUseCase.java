package com.bank.application.port.in;

import com.bank.application.model.Page;
import com.bank.application.model.PageQuery;
import com.bank.application.model.TransactionHistoryEntry;

/** List an account's transaction history, newest first, paginated. */
public interface GetAccountStatementUseCase {

    Page<TransactionHistoryEntry> getStatement(Long accountId, PageQuery query);
}
