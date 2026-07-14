# Ledger — banking frontend

A single-page frontend for the banking service. It talks to the REST API through a
small client (`api.js`) that centralizes the **HTTP Basic** auth header, the
**`Idempotency-Key`** on every money movement, and the **error-code → message** mapping.

It ships with a built-in **in-memory demo backend** (`mock-server.js`) that mirrors the
real API's request/response shape, so the whole app is fully usable with no server
running. Flip a switch in **Connection** settings to point it at your real API instead.

## Files

| File | Role |
|---|---|
| `index.html` | The UI: login/register, dashboard, account detail + paginated history, deposit/withdraw/transfer modals, admin panel, toasts. Light/dark theme. |
| `api.js` | API client — Basic auth, Idempotency-Key, friendly error messages. Same code path for real API and mock. |
| `mock-server.js` | In-memory double-entry ledger (money as integer cents, seeded demo data). |
| `support.js` | Render runtime for the UI template (loads React from unpkg and mounts on load). |

## Run it

Serve the folder over **http://** (ES modules don't load from `file://`). Any static
server works:

```bash
# from this folder
python -m http.server 5173
#   → open http://localhost:5173
```

Needs internet on first load (the runtime fetches React from unpkg).

### Demo logins (mock backend, on by default)

| Email | Password | Role |
|---|---|---|
| `alex@ledger.test` | `Password123` | CUSTOMER (2 accounts, transaction history) |
| `admin@ledger.test` | `Admin12345` | ADMIN (can freeze/close any account) |

The login screen has one-click "Use →" buttons for both. Transfer target account id
`1010` (Sam) exists as a demo destination.

Things worth demonstrating: idempotent retry (an amount ending in **`.77`** triggers a
one-time concurrency conflict, then the "Try again (same request)" button replays the
**same** Idempotency-Key and succeeds without double-charging); a wrong password 5× locks
the account (429); admin freezing an account then a deposit failing with
`ACCOUNT_NOT_ACTIVE`.

## Point it at the real backend

Open **Connection** (top bar) → turn **Demo backend** off → set the base URL to your
Spring app (e.g. `http://localhost:8080`) → Apply. Auth, idempotency and error handling
all work unchanged against the real API.

Two things are needed on the **backend** before real-API mode works end-to-end — the mock
is slightly ahead of the current API:

1. **CORS.** The browser origin (e.g. `http://localhost:5173`) must be in
   `banking.security.cors.allowed-origins`, or the browser will block every call.
2. **Two endpoints the UI expects that aren't in the API yet:**
   - `GET /api/customers/me` — the app resolves the logged-in identity from the Basic
     credentials. (Alternative: change `api.js` `authenticate()` to call an endpoint that
     exists.)
   - `GET /api/accounts` — a "list my accounts" endpoint for the dashboard. The current
     API only exposes `GET /api/accounts/{id}`.

   Also confirm `POST /api/accounts` accepts a `type` field (`CHECKING`/`SAVINGS`); if it
   only takes `currency`, drop `type` from `api.js` `openAccount()`.

Until those exist, keep the demo backend on for presentations — it exercises the exact
same frontend code and request contract.
