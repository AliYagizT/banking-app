-- Idempotency for money-moving operations. A client supplies a key with each
-- deposit/withdraw/transfer. The UNIQUE constraint makes a replay collide; the
-- stored response is returned instead of moving money a second time.
--
-- request_hash lets us detect a key being reused with DIFFERENT parameters,
-- which is a client error rather than a legitimate retry.
CREATE TABLE idempotency_key (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    idempotency_key  VARCHAR(80)  NOT NULL,
    operation_type   VARCHAR(20)  NOT NULL,
    request_hash     VARCHAR(64)  NOT NULL,
    operation_id     UUID         NOT NULL,
    response_payload JSONB,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT chk_idempotency_operation_type CHECK (operation_type IN ('DEPOSIT', 'WITHDRAWAL', 'TRANSFER'))
);
