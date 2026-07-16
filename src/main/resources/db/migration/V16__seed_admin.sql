-- Seed a bootstrap administrator so the admin console is reachable out of the box. The
-- Firebase account for this email is provisioned by StaffFirebaseSeeder (run once with
-- banking.firebase.seed-staff=true); the login password is the configured staff password.
-- No password_hash is stored (credentials live in Firebase). Change/remove before any real
-- deployment.
INSERT INTO customer (full_name, email, status, role)
VALUES ('System Admin', 'admin@bank.local', 'ACTIVE', 'ADMIN');
