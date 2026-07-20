-- Credentials moved to the external identity provider (Firebase); the backend no longer
-- stores passwords. Customers created under token auth have no password hash, so drop the
-- NOT NULL constraint. The column is kept (nullable) for rows seeded before this change.
ALTER TABLE customer
    ALTER COLUMN password_hash DROP NOT NULL;
