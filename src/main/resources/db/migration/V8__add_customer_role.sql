-- Phase (Security/RBAC): customers carry a role for role-based access control.
-- CUSTOMER is the default; ADMIN may manage any account (view/freeze/close).
ALTER TABLE customer
    ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'CUSTOMER';

ALTER TABLE customer
    ADD CONSTRAINT chk_customer_role CHECK (role IN ('CUSTOMER', 'ADMIN'));
