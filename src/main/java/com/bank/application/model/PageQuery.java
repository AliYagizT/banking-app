package com.bank.application.model;

/**
 * A framework-neutral pagination request (zero-based page index + page size), so the
 * application core never depends on Spring Data's {@code Pageable}. Persistence
 * adapters translate this into their own paging type.
 */
public record PageQuery(int page, int size) {
}
