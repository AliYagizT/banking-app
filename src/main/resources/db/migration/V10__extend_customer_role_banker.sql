-- Add the BANKER role: a relationship banker who evaluates the credit applications of
-- their assigned customers. Widen the role check constraint accordingly.
ALTER TABLE customer
    DROP CONSTRAINT chk_customer_role;

ALTER TABLE customer
    ADD CONSTRAINT chk_customer_role CHECK (role IN ('CUSTOMER', 'BANKER', 'ADMIN'));
