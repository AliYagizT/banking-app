package com.bank.adapter.out.persistence;

import com.bank.application.port.out.IdempotencyRepository;
import com.bank.domain.model.IdempotencyRecord;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Adapts the {@link IdempotencyJpaRepository} to the {@link IdempotencyRepository} output port. */
@Component
public class IdempotencyRepositoryAdapter implements IdempotencyRepository {

    private final IdempotencyJpaRepository jpa;

    public IdempotencyRepositoryAdapter(IdempotencyJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<IdempotencyRecord> findByKey(String idempotencyKey) {
        return jpa.findByIdempotencyKey(idempotencyKey);
    }

    @Override
    public IdempotencyRecord save(IdempotencyRecord record) {
        return jpa.save(record);
    }
}
