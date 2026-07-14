package com.bank.application.port.in;

import com.bank.domain.model.Account;

/** Administrative account operations, restricted to the ADMIN role by the web layer. */
public interface AdminAccountUseCase {

    /** View any account (not limited to the caller's own). */
    Account view(Long accountId);

    /** Freeze an account (no money may move until unfrozen). */
    Account freeze(Long accountId);

    /** Permanently close an account. */
    Account close(Long accountId);
}
