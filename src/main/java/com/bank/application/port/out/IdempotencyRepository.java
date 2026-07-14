package com.bank.application.port.out;

import com.bank.domain.model.IdempotencyRecord;

import java.util.Optional;

/** Output port for idempotency-key persistence (implemented by a persistence adapter). */
public interface IdempotencyRepository {

    Optional<IdempotencyRecord> findByKey(String idempotencyKey);

    IdempotencyRecord save(IdempotencyRecord record);
}
