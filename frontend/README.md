# Softtech Bank — Frontend

Single-page app for the banking service. **Build-less**: React + [htm](https://github.com/developit/htm)
and the Firebase SDK are loaded straight from a CDN as ES modules — no npm install, no bundler.

Login uses **Firebase Authentication** (email/password). After sign-in the app sends the
Firebase **ID token** as `Authorization: Bearer <token>` on every backend call; the Spring
backend verifies it and resolves the caller's role from the database.

## Files

| File | Role |
|---|---|
| `index.html` | Mount point + all styling (light/dark, responsive). |
| `config.js` | Public Firebase web config + backend base URL. |
| `firebase.js` | Firebase init + auth helpers (sign-in/up/out, ID token). |
| `api.js` | REST client: attaches the bearer token + `Idempotency-Key`, maps errors. |
| `app.js` | The React app: auth, customer (accounts/money/credit), banker, admin panels. |
| `serve.mjs` | Tiny zero-dependency static server (ES modules need http, not file://). |

## Run

The backend must be running first (see repo root). Then, from this folder:

```bash
node serve.mjs        # serves on http://localhost:5173
```

Open **http://localhost:5173**. Needs internet on first load (CDN for React/htm/Firebase).

> The backend's `banking.security.cors.allowed-origins` already allows `http://localhost:5173`.
> The API base URL is set in `config.js` (defaults to `http://localhost:8081`).

## Roles & what you can do

- **Customer** — register (profile is created after Firebase sign-up), open accounts,
  deposit / withdraw / transfer, see transaction history, browse credit products, apply
  for credit, and view the repayment (installment) plan.
- **Banker** — see the queue of assigned customers' applications, inspect the underwriting
  criteria (installment/income ratio, employment, product limits), approve (→ funds are
  disbursed) or reject. Demo login: `banker1@bank.local` / `banker123`.
- **Admin** — look up any account by id and freeze / close it.

## Notes

- The Firebase `apiKey` in `config.js` is **not** a secret (it only identifies the project);
  the real secret is the backend service-account key, which never ships to the browser.
- New users: sign up in the UI, then enter your name once to create your customer profile.
