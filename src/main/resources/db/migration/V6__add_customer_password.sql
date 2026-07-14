-- Phase 4 (Security): customers authenticate with their email as username and a
-- password. We store only a BCrypt hash, never the raw password. BCrypt hashes are
-- 60 characters; VARCHAR(100) leaves headroom for future algorithm prefixes.
ALTER TABLE customer
    ADD COLUMN password_hash VARCHAR(100) NOT NULL;
