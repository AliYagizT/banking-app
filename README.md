# Banking Application

A double-entry ledger banking service built with Java 21 and Spring Boot. It exposes a
REST API to register customers, open accounts, and move money (deposit, withdraw,
transfer) with the correctness guarantees a real banking system needs: exact decimal
money, atomic transfers, protection against the lost-update problem, idempotent
operations, and an immutable audit trail.

## Tech stack

- **Java 21**, **Spring Boot 3.3**
- **Spring Data JPA / Hibernate** over **PostgreSQL**
- **Flyway** for versioned SQL migrations (the schema is owned by SQL, not Hibernate)
- **Spring Security** (HTTP Basic) for authentication and per-account authorization
- **springdoc-openapi** for Swagger UI
- **JUnit 5 + Testcontainers** — every integration test runs against a real PostgreSQL
  in Docker (never H2), so tests exercise the exact database the app uses

## Architecture

The project follows a **pragmatic clean (hexagonal) architecture**. Dependencies point
inward only — `adapter` → `application` → `domain` — and the application core talks to
the outside world exclusively through **ports (interfaces)**. Concretely, the core
(`application` + `domain`) contains **no Spring Data, no Spring transaction API, and no
Spring/Hibernate exception types**; those live behind ports in the outer layers.

| Layer (`com.bank.…`) | Responsibility |
|---|---|
| `domain` | Enums, the `Money` policy, and the exception hierarchy |
| `domain.model` | The entities (`Customer`, `Account`, `LedgerEntry`, `OperationLogEntry`, `IdempotencyRecord`) — the single model, JPA-annotated |
| `application.model` | Use-case input/output values (`MoneyMovementResult`, `BalanceView`, `TransactionHistoryEntry`, framework-neutral `Page`/`PageQuery`) |
| `application.port.in` | **Input ports** — use-case interfaces (`DepositUseCase`, `TransferUseCase`, …) the web adapter drives |
| `application.port.out` | **Output ports** — `CustomerRepository`, `AccountRepository`, `LedgerRepository`, …, plus `TransactionRunner` and `PasswordHasher` |
| `application.service` | Use-case implementations (`MoneyMovementService`, `AccountService`, `CustomerService`, `AccountAccessGuard`) |
| `adapter.in.web` | REST controllers, request/response DTOs, and the global exception handler |
| `adapter.out.persistence` | Spring Data JPA repositories and the adapters that implement the `port.out` interfaces |
| `infrastructure` | Framework wiring: `security` (Spring Security), `transaction` (the `TransactionRunner` implementation), `config` (OpenAPI) |

**How the core stays framework-free:** the `SpringTransactionRunner` (infrastructure)
owns transaction boundaries and translates the persistence provider's failures into
domain exceptions (`ConcurrencyConflictException`, `DuplicateIdempotencyKeyException`), so
the money-movement service retries and replays purely on domain types. Persistence
adapters map Spring Data's `Page`/`Pageable` to the framework-neutral `Page`/`PageQuery`.

The **application layer is independent of the web and persistence layers**, so the core
money-movement logic is unit/integration-tested directly without going through HTTP.

*Pragmatic concessions (deliberate, to avoid over-engineering at this scale):* the domain
entities keep their JPA annotations and act as the single model (no separate persistence
model + mappers); DI/config annotations (`@Service`, `@Component`, `@Value`) and Jackson
are permitted in the application layer.

### Domain model: a double-entry ledger

Balances are **not** a freely mutable field. The source of truth is an append-only
`ledger_entry` table:

- Every money movement writes **two immutable legs** — a `DEBIT` and a `CREDIT` — that
  always net to zero and are written together in one transaction. The ledger can never
  hold a half-finished movement.
- Deposits and withdrawals balance against a seeded **system `EXTERNAL-CASH` account**,
  so even cash entering or leaving the bank is recorded as a balanced double entry. The
  system account is hidden from all customer-facing operations.
- `account.balance` is a **cached** column for fast reads. It is only ever updated
  inside the same transaction that writes the ledger legs, and a test asserts it never
  drifts from `SUM(credits) − SUM(debits)`.
- Ledger rows are never updated or deleted. Corrections would be made by appending new
  **compensating** entries.

## Banking-correctness decisions

1. **Money is `BigDecimal`, never `double`/`float`.** All amounts are normalized to
   **scale 2** with an explicit **`RoundingMode.HALF_EVEN`** (banker's rounding) in
   `domain/Money`. Client amounts carrying more than two decimals are rejected rather
   than silently rounded. Database columns are `NUMERIC(19,2)`.

2. **Atomicity.** A transfer debits the source and credits the destination in a single
   database transaction; either both legs commit or neither does. Partial transfers are
   impossible.

3. **Concurrency — optimistic locking + bounded retry.** Each `account` row carries a
   `@Version`. Concurrent writers racing on the same account cause the late commit to
   fail; the `TransactionRunner` surfaces this as a domain `ConcurrencyConflictException`,
   which the service catches and **retries on a fresh transaction** (bounded count, linear
   backoff with jitter).
   - *Why optimistic, not pessimistic?* It never holds a row lock across the
     transaction, keeping throughput high in the common low-contention case; under a hot
     row the retries effectively serialize writers. The retry bound is configurable
     (`banking.optimistic-lock.*`).
   - *Why a dedicated `TransactionRunner` (each attempt in a new transaction)?* Each retry
     needs a brand-new transaction — a failed transaction is marked rollback-only, so a
     retry loop *inside* one transaction cannot work. The runner starts a fresh
     `REQUIRES_NEW` transaction per attempt, which also keeps Spring's transaction API out
     of the core.
   - A test fires **20 concurrent transfers** against a hot account and asserts the final
     balance is exact.

4. **Idempotency.** Every money-moving operation takes a client-supplied key (the
   `Idempotency-Key` HTTP header). Keys are persisted with a hash of the request
   parameters:
   - Replaying the **same key with the same parameters** returns the original result
     (`replayed: true`) without moving money again.
   - Reusing a key with **different parameters** is rejected as a conflict.
   - A concurrent race on the same key is resolved by a unique constraint: the loser
     re-reads and returns the winner's committed result.

5. **Validation.** Non-positive amounts, insufficient funds, same source/destination,
   and non-existent/inactive accounts are all rejected with clear, structured errors.

6. **Audit.**
   - Each ledger entry carries a timestamp and the `operationId` of the movement that
     created it (linking both legs).
   - A dedicated, immutable **`operation_log`** records one operation-centric row per
     executed movement (type, idempotency key, the accounts involved, amount,
     timestamp) — written in the same transaction as the ledger, so the audit trail can
     never diverge from the money that actually moved. Replays do not append a row.

## Security

Authentication is **HTTP Basic**: the customer's email is the username and the password
is stored only as a **BCrypt** hash. The API is stateless (no sessions; CSRF disabled
accordingly). There is **no shared/API-key secret**, so a frontend never embeds a
credential that isn't the end user's own. See [SECURITY.md](SECURITY.md) for the full
overview; in short:

- **RBAC:** roles `CUSTOMER` (default) and `ADMIN`. `/api/admin/**` requires `ROLE_ADMIN`;
  a `CUSTOMER` may access and operate only on **their own** accounts (row-level, enforced
  per request by `AccountAccessGuard`). Admin elevation is operational, not a public API.
- **Cross-customer and non-existent accounts both return `404`, not `403`**, so the API
  never reveals whether another customer's account id exists (no enumeration).
- **Brute-force guard:** consecutive failed logins are limited **per IP**; a blocked IP
  gets `429` before credentials are re-checked (`banking.security.login.*`).
- **CORS** is denied by default; browser origins are opted in via
  `banking.security.cors.allowed-origins`.
- **Secrets** (e.g. the DB password) are read at runtime from **HashiCorp Vault** (Spring
  Cloud Vault), never committed to git or baked into images.
- `POST /api/customers` (registration) and the Swagger/OpenAPI endpoints are public;
  everything else requires authentication. Auth failures return the standard JSON error
  shape (`UNAUTHENTICATED` → 401, `ACCESS_DENIED` → 403, `TOO_MANY_ATTEMPTS` → 429).

## REST API

DTOs are used everywhere — entities are never exposed. All errors share one shape with a
stable machine-readable `code` (e.g. `INSUFFICIENT_FUNDS`, `VALIDATION_ERROR`,
`NOT_FOUND`, `CONCURRENCY_CONFLICT`).

| Method & path | Description |
|---|---|
| `POST /api/customers` | Register a customer (public) |
| `GET /api/customers/{id}` | Fetch your own customer record |
| `POST /api/accounts` | Open an account for the authenticated customer |
| `GET /api/accounts/{id}` | Fetch one of your accounts |
| `GET /api/accounts/{id}/balance` | Current balance |
| `GET /api/accounts/{id}/transactions?page=&size=` | Paginated statement, newest first |
| `POST /api/accounts/{id}/deposits` | Deposit (needs `Idempotency-Key`) |
| `POST /api/accounts/{id}/withdrawals` | Withdraw (needs `Idempotency-Key`) |
| `POST /api/transfers` | Transfer from one of your accounts (needs `Idempotency-Key`) |
| `GET /api/admin/accounts/{id}` | **ADMIN** — view any account |
| `POST /api/admin/accounts/{id}/freeze` | **ADMIN** — freeze an account |
| `POST /api/admin/accounts/{id}/close` | **ADMIN** — close an account |

Transaction history is **paginated** (`page`, `size`; default size 20, max 100) with a
deterministic newest-first ordering (`created_at desc, id desc`) so page boundaries are
stable even for entries sharing a timestamp. The response is an explicit
`PagedResponse` envelope (`content`, `page`, `size`, `totalElements`, `totalPages`,
`first`, `last`).

**Swagger UI:** `http://localhost:8080/swagger-ui.html` — use the **Authorize** button
to supply Basic credentials. OpenAPI JSON at `/v3/api-docs`.

## Database migrations

Flyway applies versioned SQL from `src/main/resources/db/migration`:

| Version | Migration |
|---|---|
| V1 | `customer` |
| V2 | `account` |
| V3 | `ledger_entry` |
| V4 | `idempotency_key` |
| V5 | seed the system `EXTERNAL-CASH` account |
| V6 | add `password_hash` to `customer` |
| V7 | `operation_log` (audit trail) |
| V8 | add `role` to `customer` (RBAC) |

Hibernate runs with `ddl-auto: validate` — it never changes the schema, only checks that
the entities match what Flyway built.

## Running the application

### Option A — full stack in Docker (recommended)

The sibling **`banking-infra`** repo brings up Vault + PostgreSQL + the app + a seeder
with one command (secrets live in Vault, demo data is seeded through the real API):

```bash
cd ../banking-infra
cp .env.example .env      # edit DB_PASSWORD / VAULT_TOKEN
docker compose up --build
```

Then open Swagger UI and log in as a seeded user (see that repo's README).

### Option B — run the app directly

Requires a PostgreSQL database. Configure via environment variables (defaults shown);
in production the DB password comes from Vault (`VAULT_URI` / `VAULT_TOKEN`) instead:

```
DB_URL=jdbc:postgresql://localhost:5432/banking
DB_USERNAME=banking
DB_PASSWORD=banking
```

```bash
# Run (Flyway migrates on startup)
./mvnw spring-boot:run
```

Then open Swagger UI, register a customer via `POST /api/customers`, and use those
credentials (Authorize button) for the rest of the API.

## Running the tests

Integration tests start a real PostgreSQL via Testcontainers, so **Docker must be
running**.

```bash
./mvnw test     # unit tests only (fast, no Docker)
./mvnw verify   # full suite: unit + Testcontainers integration tests
```

The suite covers happy paths plus the hard cases: concurrent transfers against a hot
account, idempotent replays, insufficient funds, validation failures, authentication and
cross-customer authorization, pagination, and the audit log.
