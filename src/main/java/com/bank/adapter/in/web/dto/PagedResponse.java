package com.bank.adapter.in.web.dto;

import com.bank.application.model.Page;

import java.util.List;

/**
 * A stable, explicit pagination envelope. We map the application's framework-neutral
 * {@link Page} into this record so the JSON shape is owned by the API (not by a
 * library's page type).
 */
public record PagedResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last) {

    public static <T> PagedResponse<T> from(Page<T> page) {
        return new PagedResponse<>(
                page.content(),
                page.page(),
                page.size(),
                page.totalElements(),
                page.totalPages(),
                page.first(),
                page.last());
    }
}
