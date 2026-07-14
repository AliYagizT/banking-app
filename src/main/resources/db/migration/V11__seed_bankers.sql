-- Seed two demo bankers so credit applications can be assigned and evaluated out of the
-- box (dev/demo). They authenticate with their email and the password "banker123".
-- The password_hash values are BCrypt hashes of "banker123"; the raw password is never
-- stored. Change or remove these before any real deployment.
INSERT INTO customer (full_name, email, password_hash, status, role)
VALUES
    ('Banker One', 'banker1@bank.local',
     '$2a$10$LxrqwXXeJeP.rvu53Nzy4.oX6di5xcHNBXsFzRbZBL22Tw18fGAJm', 'ACTIVE', 'BANKER'),
    ('Banker Two', 'banker2@bank.local',
     '$2a$10$Gzt/isqERP/O0M.45L./o.JKvs2Q3Qy42/kt6Vq.1mJ3EdUHbxZvS', 'ACTIVE', 'BANKER');
