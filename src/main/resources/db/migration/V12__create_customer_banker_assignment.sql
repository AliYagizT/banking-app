-- Links each customer to the banker responsible for their credit applications. Assigned
-- (randomly) at registration; one active assignment per customer.
CREATE TABLE customer_banker_assignment (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    customer_id BIGINT      NOT NULL REFERENCES customer (id),
    banker_id   BIGINT      NOT NULL REFERENCES customer (id),
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_cba_customer UNIQUE (customer_id)
);

CREATE INDEX idx_cba_banker ON customer_banker_assignment (banker_id);
