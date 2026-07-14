# Security Overview

This document summarises the software-level security controls in the banking service
and how to operate it safely.

## Authentication — per-user, no client-side secrets

- Authentication is **HTTP Basic**: each request carries the customer's **own** email +
  password. There is **no shared/API-key secret** that a browser or mobile app must
  embed — a frontend never holds a credential that isn't the end user's own. This avoids
  the classic "API key shipped in the JS bundle" leak.
- Passwords are stored only as **BCrypt** hashes (`password_hash`); the raw password is
  never persisted or logged.
- The API is **stateless** (no sessions, no cookies) so CSRF is not applicable and is
  disabled deliberately.
- **Must run behind TLS.** Basic credentials are sent on every request; terminate HTTPS
  at the ingress/reverse proxy and redirect HTTP→HTTPS. Do not expose the app over plain
  HTTP in production.

> For browser SPAs, prefer a backend-for-frontend or short-lived token exchange over
> sending Basic credentials from JavaScript — but even then, no long-lived secret belongs
> in client code.

## Authorization — RBAC + row-level ownership

- **Roles:** `CUSTOMER` (default) and `ADMIN` (`role` column on `customer`). Authorities
  are exposed as `ROLE_CUSTOMER` / `ROLE_ADMIN`.
- `/api/admin/**` requires `ROLE_ADMIN` (view any account, freeze, close).
- A `CUSTOMER` may access and operate only on **their own** accounts. This is a row-level
  rule enforced per request by `AccountAccessGuard`; a non-owned or non-existent account
  returns **404** (not 403) so account ids cannot be enumerated.
- Admin elevation is an **operational** action (done out-of-band, e.g. by the infra
  seeder), not a public endpoint — there is no way to self-promote to ADMIN via the API.

## Brute-force protection

- `LoginAttemptService` tracks consecutive failed logins **per client IP** and blocks
  that IP for a cool-off window once a threshold is exceeded (`BruteForceGuardFilter`
  returns **429** before credentials are re-checked).
- Keyed by IP, not username, so an attacker cannot lock a victim out of their own
  account. Configurable via `banking.security.login.max-attempts` /
  `banking.security.login.block-minutes`.
- State is in-memory (per instance); a multi-node deployment should back it with a shared
  store (e.g. Redis) behind the same interface.

## CORS

- Cross-origin requests are **denied by default**. Allowed browser origins are opted in
  explicitly via `banking.security.cors.allowed-origins` (env `CORS_ALLOWED_ORIGINS`,
  comma-separated). No wildcard-with-credentials.

## Secrets management — HashiCorp Vault

- Secrets (e.g. the database password) are **not** committed to git and not baked into
  images. The app reads them at runtime from **HashiCorp Vault** via Spring Cloud Vault
  (`optional:vault://secret/banking`).
- The Vault connection uses `VAULT_URI` / `VAULT_TOKEN` (env). In the `banking-infra`
  repo, a Vault dev container holds the secrets and the app pulls them on boot.
- Locally/in tests Vault is disabled (`spring.cloud.vault.enabled=false`); tests get the
  datasource from Testcontainers.

## Error handling

- All errors return a single JSON shape with a stable machine-readable `code`; internal
  exceptions are logged server-side and never leaked to clients (generic `INTERNAL_ERROR`
  with HTTP 500).

## What is still required before real public/production use

Software controls above are in place, but a regulated public banking service still needs,
beyond this codebase: TLS everywhere + secret rotation, centralized audit of auth events,
observability (health checks, metrics, tracing), rate limiting at the edge, data-at-rest
encryption, backups/DR, an actor field on the audit log, plus the full compliance layer
(KYC/AML, KVKK/GDPR, licensing).
