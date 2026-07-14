// API client: centralizes Basic-auth header, Idempotency-Key handling, and
// error-code -> friendly message mapping. Works against a real REST API or the
// in-memory MockServer (same request/response contract).

import { MockServer } from './mock-server.js';

export const ERROR_MESSAGES = {
  UNAUTHENTICATED: 'Incorrect email or password.',
  ACCESS_DENIED: "You don't have permission to do that.",
  TOO_MANY_ATTEMPTS: 'Too many attempts. Please wait a few minutes and try again.',
  INSUFFICIENT_FUNDS: 'Insufficient funds for this transaction.',
  VALIDATION_ERROR: 'Please check the details and try again.',
  NOT_FOUND: "We couldn't find that account.",
  CONCURRENCY_CONFLICT: 'This account was updated at the same time. Please try again.',
  ACCOUNT_NOT_ACTIVE: "This account isn't active, so money can't move right now.",
  NETWORK: "Can't reach the server. Check your connection and try again.",
  UNKNOWN: 'Something went wrong. Please try again.',
};

const STATUS_TO_CODE = { 401: 'UNAUTHENTICATED', 403: 'ACCESS_DENIED', 429: 'TOO_MANY_ATTEMPTS' };

export class ApiError extends Error {
  constructor(code, message, status, raw) {
    super(message || ERROR_MESSAGES[code] || ERROR_MESSAGES.UNKNOWN);
    this.code = code || 'UNKNOWN';
    this.status = status;
    this.raw = raw;
    this.friendly = message && code === 'VALIDATION_ERROR' ? message
      : (ERROR_MESSAGES[this.code] || ERROR_MESSAGES.UNKNOWN);
  }
}

export const newIdempotencyKey = () =>
  (crypto.randomUUID ? crypto.randomUUID()
    : 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, c => {
        const r = (Math.random() * 16) | 0; return (c === 'x' ? r : (r & 0x3) | 0x8).toString(16);
      }));

export class ApiClient {
  constructor({ baseUrl = 'http://localhost:8080', useMock = true } = {}) {
    this.baseUrl = baseUrl.replace(/\/$/, '');
    this.useMock = useMock;
    this.auth = null; // { email, password } kept in memory only
    this.mock = useMock ? new MockServer() : null;
  }

  setBaseUrl(url) { this.baseUrl = String(url).replace(/\/$/, ''); }
  setMode(useMock) {
    this.useMock = useMock;
    if (useMock && !this.mock) this.mock = new MockServer();
  }
  setAuth(email, password) { this.auth = { email, password }; }
  clearAuth() { this.auth = null; }

  async _send({ method, path, query, body, idempotencyKey, auth }) {
    const creds = auth === undefined ? this.auth : auth;
    if (this.useMock) {
      try {
        const res = await this.mock.request({ method, path, query, body, auth: creds, idempotencyKey });
        return res.body;
      } catch (e) {
        const code = e.code || STATUS_TO_CODE[e.status] || 'UNKNOWN';
        throw new ApiError(code, e.message, e.status, e);
      }
    }
    // Real transport
    let res;
    try {
      const qs = query ? '?' + new URLSearchParams(query).toString() : '';
      const headers = { Accept: 'application/json' };
      if (creds) headers['Authorization'] = 'Basic ' + btoa(creds.email + ':' + creds.password);
      if (body) headers['Content-Type'] = 'application/json';
      if (idempotencyKey) headers['Idempotency-Key'] = idempotencyKey;
      res = await fetch(this.baseUrl + path + qs, {
        method, headers, body: body ? JSON.stringify(body) : undefined,
      });
    } catch (netErr) {
      throw new ApiError('NETWORK', ERROR_MESSAGES.NETWORK, 0, netErr);
    }
    let data = null;
    try { data = await res.json(); } catch (_) { /* empty body */ }
    if (!res.ok) {
      const code = (data && data.code) || STATUS_TO_CODE[res.status] || 'UNKNOWN';
      throw new ApiError(code, data && data.message, res.status, data);
    }
    return data;
  }

  // ---------- auth / customer ----------
  async register({ fullName, email, password }) {
    return this._send({ method: 'POST', path: '/api/customers', body: { fullName, email, password }, auth: null });
  }
  // Resolve identity from Basic creds (validates email+password).
  async authenticate(email, password) {
    return this._send({ method: 'GET', path: '/api/customers/me', auth: { email, password } });
  }
  getCustomer(id) { return this._send({ method: 'GET', path: `/api/customers/${id}` }); }

  // ---------- accounts ----------
  async listAccounts() {
    const data = await this._send({ method: 'GET', path: '/api/accounts' });
    if (this.useMock) return data;
    return { content: (data.content || []).map(a => this._account(a)) };
  }
  async openAccount(type = 'CHECKING') {
    const data = await this._send({ method: 'POST', path: '/api/accounts', body: { currency: 'USD', type } });
    return this.useMock ? data : this._account(data);
  }
  async getAccount(id) {
    const data = await this._send({ method: 'GET', path: `/api/accounts/${id}` });
    return this.useMock ? data : this._account(data);
  }
  getBalance(id) { return this._send({ method: 'GET', path: `/api/accounts/${id}/balance` }); }
  async getTransactions(id, page = 0, size = 20) {
    const data = await this._send({ method: 'GET', path: `/api/accounts/${id}/transactions`, query: { page, size } });
    if (this.useMock) return data;
    return { ...data, content: (data.content || []).map(e => this._txEntry(e)) };
  }

  // ---------- money movements (idempotent) ----------
  async deposit(id, amount, idempotencyKey) {
    const data = await this._send({ method: 'POST', path: `/api/accounts/${id}/deposits`, body: { amount }, idempotencyKey });
    return this.useMock ? data : this._movement(data);
  }
  async withdraw(id, amount, idempotencyKey) {
    const data = await this._send({ method: 'POST', path: `/api/accounts/${id}/withdrawals`, body: { amount }, idempotencyKey });
    return this.useMock ? data : this._movement(data);
  }
  async transfer({ sourceAccountId, destinationAccountId, amount }, idempotencyKey) {
    const data = await this._send({ method: 'POST', path: '/api/transfers', body: { sourceAccountId, destinationAccountId, amount }, idempotencyKey });
    return this.useMock ? data : this._movement(data);
  }

  // ---------- admin ----------
  async adminGetAccount(id) {
    const data = await this._send({ method: 'GET', path: `/api/admin/accounts/${id}` });
    return this.useMock ? data : this._account(data);
  }
  adminFreeze(id) { return this._send({ method: 'POST', path: `/api/admin/accounts/${id}/freeze` }); }
  adminClose(id) { return this._send({ method: 'POST', path: `/api/admin/accounts/${id}/close` }); }

  // ---------- response adapters (real backend JSON -> UI shape) ----------
  // The real API has no product-level account type (its AccountType is CUSTOMER/SYSTEM)
  // and serves money as JSON numbers; these map its field names onto what the UI
  // (and the mock) already consume. Only used when talking to the real backend.
  _account(a) {
    return {
      id: a.id, accountNumber: a.accountNumber, type: a.type || 'CHECKING',
      currency: a.currency, status: a.status, balance: a.balance,
      ownerId: a.customerId, ownerName: a.ownerName, ownerEmail: a.ownerEmail,
    };
  }
  _txEntry(e) {
    const credit = e.direction === 'CREDIT';
    const label = { DEPOSIT: 'Deposit', WITHDRAWAL: 'Withdrawal',
      TRANSFER: credit ? 'Transfer received' : 'Transfer sent' }[e.type];
    return {
      id: e.entryId, direction: e.direction, amount: e.amount,
      balanceAfter: e.balanceAfter, createdAt: e.timestamp,
      description: label || (credit ? 'Credit' : 'Debit'),
    };
  }
  _movement(m) {
    return { accountId: m.primaryAccountId, balance: m.primaryBalance, replayed: !!m.replayed };
  }
}
