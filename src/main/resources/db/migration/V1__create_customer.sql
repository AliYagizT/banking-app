-- Customers who own accounts. `version` backs JPA optimistic locking.
CREATE TABLE customer (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    full_name  VARCHAR(200) NOT NULL,
    email      VARCHAR(320) NOT NULL,
    status     VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version    BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uq_customer_email UNIQUE (email),
    CONSTRAINT chk_customer_status CHECK (status IN ('ACTIVE', 'CLOSED'))
);
