package com.bank.application.model;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * A framework-neutral page of results, so the application core never returns Spring
 * Data's {@code Page}. Persistence adapters build this from their own paging type;
 * the web layer maps it to its response envelope.
 */
public record Page<T>(List<T> content, int page, int size, long totalElements) {

    public int totalPages() {
        if (size <= 0) {
            return 0;
        }
        return (int) Math.ceil((double) totalElements / size);
    }

    public boolean first() {
        return page <= 0;
    }

    public boolean last() {
        return page >= totalPages() - 1;
    }

    /** Return a new page with each element transformed, preserving the paging metadata. */
    public <R> Page<R> map(Function<? super T, ? extends R> mapper) {
        List<R> mapped = new ArrayList<>(content.size());
        for (T item : content) {
            mapped.add(mapper.apply(item));
        }
        return new Page<>(mapped, page, size, totalElements);
    }
}
