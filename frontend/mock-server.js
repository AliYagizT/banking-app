// In-memory double-entry ledger backend for the Ledger banking demo.
// Mirrors the real REST API shape so the ApiClient can talk to it identically.
// Money is stored as integer cents — never floats.

// ---------- decimal helpers (integer cents) ----------
export function parseAmount(str) {
  if (typeof str !== 'string') str = String(str);
  str = str.trim();
  if (!/^\d+(\.\d{1,2})?$/.test(str)) {
    throw { code: 'VALIDATION_ERROR', message: 'Enter an amount like 100.00.' };
  }
  const [whole, frac = ''] = str.split('.');
  const cents = parseInt(whole, 10) * 100 + parseInt((frac + '00').slice(0, 2), 10);
  return cents;
}
export function formatCents(cents) {
  const neg = cents < 0;
  const abs = Math.abs(cents);
  const s = String(abs).padStart(3, '0');
  return (neg ? '-' : '') + s.slice(0, -2) + '.' + s.slice(-2);
}

let SEQ = 1000;
const nextId = () => ++SEQ;

export class MockServer {
  constructor() {
    this.customers = [];
    this.accounts = [];
    this.entries = [];            // ledger entries (one per account side of a movement)
    this.idempotency = new Map(); // key -> stored response body
    this.attempts = new Map();    // email -> {count, until}
    this.conflictSeen = new Set(); // idempotency keys that already hit a simulated conflict
    this._seed();
  }

  _seed() {
    // System account (hidden from customers)
    const system = { id: nextId(), customerId: null, accountNumber: 'EXTERNAL-CASH', type: 'SYSTEM', currency: 'USD', status: 'ACTIVE', system: true, balance: 0 };
    this.system = system;
    this.accounts.push(system);

    const alex = { id: nextId(), fullName: 'Alex Rivera', email: 'alex@ledger.test', password: 'Password123', role: 'CUSTOMER' };
    const admin = { id: nextId(), fullName: 'Dana Okoye', email: 'admin@ledger.test', password: 'Admin12345', role: 'ADMIN' };
    const sam = { id: nextId(), fullName: 'Sam Cole', email: 'sam@ledger.test', password: 'Password123', role: 'CUSTOMER' };
    this.customers.push(alex, admin, sam);

    const checking = { id: nextId(), customerId: alex.id, accountNumber: '10004821', type: 'CHECKING', currency: 'USD', status: 'ACTIVE', balance: 0 };
    const savings = { id: nextId(), customerId: alex.id, accountNumber: '10004822', type: 'SAVINGS', currency: 'USD', status: 'ACTIVE', balance: 0 };
    const adminAcct = { id: nextId(), customerId: admin.id, accountNumber: '10000001', type: 'CHECKING', currency: 'USD', status: 'ACTIVE', balance: 0 };
    const samAcct = { id: nextId(), customerId: sam.id, accountNumber: '10007777', type: 'CHECKING', currency: 'USD', status: 'ACTIVE', balance: 0 };
    this.accounts.push(checking, savings, adminAcct, samAcct);
    this.demoDestId = samAcct.id;

    // Build history with a timeline of movements (oldest first).
    const day = 86400000;
    const now = Date.now();
    const T = (d, h = 10) => now - d * day + h * 3600000;
    const steps = [
      [checking, 'CREDIT', '5200.00', 'Payroll deposit — Northwind Labs', T(28)],
      [savings,  'CREDIT', '12000.00', 'Opening transfer', T(27)],
      [checking, 'DEBIT',  '1450.00', 'Rent — Ashwood Properties', T(24)],
      [checking, 'DEBIT',  '86.40',   'Grocery — Merit Market', T(22)],
      [savings,  'CREDIT', '3000.00', 'Monthly savings', T(20)],
      [checking, 'DEBIT',  '54.10',   'Utilities — Cityline Power', T(18)],
      [checking, 'CREDIT', '1800.00', 'Payroll deposit — Northwind Labs', T(14)],
      [checking, 'DEBIT',  '220.00',  'Transfer to Savings', T(14, 11)],
      [savings,  'CREDIT', '220.00',  'Transfer from Checking', T(14, 11)],
      [checking, 'DEBIT',  '39.99',   'Subscription — Cloudline', T(10)],
      [checking, 'DEBIT',  '128.75',  'Restaurant — Bellwether', T(7)],
      [checking, 'DEBIT',  '200.00',  'ATM withdrawal', T(4)],
      [savings,  'DEBIT',  '10.00',   'Account maintenance', T(3)],
      [checking, 'CREDIT', '75.00',   'Refund — Merit Market', T(1)],
      [adminAcct, 'CREDIT', '2500.00', 'Payroll deposit', T(15)],
      [samAcct,   'CREDIT', '900.00',  'Payroll deposit', T(12)],
    ];
    for (const [acct, dir, amt, desc, ts] of steps) {
      const c = parseAmount(amt);
      acct.balance += dir === 'CREDIT' ? c : -c;
      this.entries.push({
        id: nextId(), accountId: acct.id, direction: dir, amount: formatCents(c),
        balanceAfter: formatCents(acct.balance), description: desc,
        createdAt: new Date(ts).toISOString(),
      });
    }
  }

  // ---------- auth ----------
  _authenticate(auth) {
    if (!auth || !auth.email) throw { status: 401, code: 'UNAUTHENTICATED', message: 'Authentication required.' };
    const rec = this.attempts.get(auth.email);
    if (rec && rec.until > Date.now()) {
      throw { status: 429, code: 'TOO_MANY_ATTEMPTS', message: 'Too many attempts.' };
    }
    const cust = this.customers.find(c => c.email.toLowerCase() === String(auth.email).toLowerCase());
    if (!cust || cust.password !== auth.password) {
      const r = this.attempts.get(auth.email) || { count: 0, until: 0 };
      r.count += 1;
      if (r.count >= 5) { r.until = Date.now() + 3 * 60000; r.count = 0; }
      this.attempts.set(auth.email, r);
      throw { status: 401, code: 'UNAUTHENTICATED', message: 'Incorrect email or password.' };
    }
    this.attempts.delete(auth.email);
    return cust;
  }

  _publicCustomer(c) { return { id: c.id, fullName: c.fullName, email: c.email, role: c.role }; }
  _publicAccount(a) {
    return { id: a.id, accountNumber: a.accountNumber, type: a.type, currency: a.currency, status: a.status, balance: formatCents(a.balance), ownerId: a.customerId };
  }

  _myAccounts(custId) {
    return this.accounts.filter(a => !a.system && a.customerId === custId).map(a => this._publicAccount(a));
  }

  _err(status, code, message) { return { status, code, message }; }

  _postMovement(account, direction, cents, description) {
    account.balance += direction === 'CREDIT' ? cents : -cents;
    const e = {
      id: nextId(), accountId: account.id, direction, amount: formatCents(cents),
      balanceAfter: formatCents(account.balance), description,
      createdAt: new Date().toISOString(),
    };
    this.entries.push(e);
    return e;
  }

  // Simulated optimistic-lock conflict for amounts ending in .77 (first try only).
  _maybeConflict(idemKey, cents) {
    if (cents % 100 === 77 && idemKey && !this.conflictSeen.has(idemKey)) {
      this.conflictSeen.add(idemKey);
      throw this._err(409, 'CONCURRENCY_CONFLICT', 'This account was updated at the same time.');
    }
  }

  // ---------- request router ----------
  // Returns { status, body }. Throws {status, code, message} on error.
  async request({ method, path, query, body, auth, idempotencyKey }) {
    // Simulate a little latency for realistic loading states.
    await new Promise(r => setTimeout(r, 260));

    // Public register
    if (method === 'POST' && path === '/api/customers') {
      const { fullName, email, password } = body || {};
      if (!fullName || !fullName.trim()) throw this._err(422, 'VALIDATION_ERROR', 'Enter your full name.');
      if (!email || !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email)) throw this._err(422, 'VALIDATION_ERROR', 'Enter a valid email address.');
      if (!password || password.length < 8 || !/[a-zA-Z]/.test(password) || !/\d/.test(password)) {
        throw this._err(422, 'VALIDATION_ERROR', 'Password must be at least 8 characters and include a letter and a number.');
      }
      if (this.customers.some(c => c.email.toLowerCase() === email.toLowerCase())) {
        throw this._err(422, 'VALIDATION_ERROR', 'That email is already registered.');
      }
      const cust = { id: nextId(), fullName: fullName.trim(), email: email.trim(), password, role: 'CUSTOMER' };
      this.customers.push(cust);
      return { status: 201, body: this._publicCustomer(cust) };
    }

    // Everything else requires auth
    const me = this._authenticate(auth);
    const isAdmin = me.role === 'ADMIN';

    // whoami (used by login to resolve identity from Basic creds)
    if (method === 'GET' && path === '/api/customers/me') {
      return { status: 200, body: this._publicCustomer(me) };
    }

    let m;
    // GET /api/customers/{id}
    if (method === 'GET' && (m = path.match(/^\/api\/customers\/(\d+)$/))) {
      const id = +m[1];
      if (id !== me.id && !isAdmin) throw this._err(403, 'ACCESS_DENIED', 'You can only view your own record.');
      const c = this.customers.find(x => x.id === id);
      if (!c) throw this._err(404, 'NOT_FOUND', 'Customer not found.');
      return { status: 200, body: this._publicCustomer(c) };
    }

    // POST /api/accounts  -> open account
    if (method === 'POST' && path === '/api/accounts') {
      const type = (body && body.type) || 'CHECKING';
      const acct = { id: nextId(), customerId: me.id, accountNumber: String(10000000 + Math.floor(Math.random() * 8999999)), type, currency: 'USD', status: 'ACTIVE', balance: 0 };
      this.accounts.push(acct);
      return { status: 201, body: this._publicAccount(acct) };
    }

    // list my accounts (convenience, not in the spec but used by dashboard)
    if (method === 'GET' && path === '/api/accounts') {
      return { status: 200, body: { content: this._myAccounts(me.id) } };
    }

    const ownedOrAdmin = (acct) => acct && (isAdmin || acct.customerId === me.id);
    const findAccount = (id) => this.accounts.find(a => a.id === +id);

    // GET /api/accounts/{id}
    if (method === 'GET' && (m = path.match(/^\/api\/accounts\/(\d+)$/))) {
      const a = findAccount(m[1]);
      if (!a || a.system) throw this._err(404, 'NOT_FOUND', 'Account not found.');
      if (!ownedOrAdmin(a)) throw this._err(403, 'ACCESS_DENIED', 'This account is not yours.');
      return { status: 200, body: this._publicAccount(a) };
    }

    // GET /api/accounts/{id}/balance
    if (method === 'GET' && (m = path.match(/^\/api\/accounts\/(\d+)\/balance$/))) {
      const a = findAccount(m[1]);
      if (!a || a.system) throw this._err(404, 'NOT_FOUND', 'Account not found.');
      if (!ownedOrAdmin(a)) throw this._err(403, 'ACCESS_DENIED', 'This account is not yours.');
      return { status: 200, body: { accountId: a.id, balance: formatCents(a.balance), currency: a.currency } };
    }

    // GET /api/accounts/{id}/transactions
    if (method === 'GET' && (m = path.match(/^\/api\/accounts\/(\d+)\/transactions$/))) {
      const a = findAccount(m[1]);
      if (!a || a.system) throw this._err(404, 'NOT_FOUND', 'Account not found.');
      if (!ownedOrAdmin(a)) throw this._err(403, 'ACCESS_DENIED', 'This account is not yours.');
      const page = Math.max(0, parseInt(query?.page ?? '0', 10) || 0);
      const size = Math.min(100, Math.max(1, parseInt(query?.size ?? '20', 10) || 20));
      const all = this.entries.filter(e => e.accountId === a.id)
        .sort((x, y) => new Date(y.createdAt) - new Date(x.createdAt));
      const totalElements = all.length;
      const totalPages = Math.max(1, Math.ceil(totalElements / size));
      const content = all.slice(page * size, page * size + size);
      return { status: 200, body: { content, page, size, totalElements, totalPages, first: page === 0, last: page >= totalPages - 1 } };
    }

    // Money movements (idempotent)
    const replay = (key) => key && this.idempotency.has(key) ? { ...this.idempotency.get(key), replayed: true } : null;
    const store = (key, resultBody) => { if (key) this.idempotency.set(key, resultBody); return resultBody; };

    // POST /api/accounts/{id}/deposits
    if (method === 'POST' && (m = path.match(/^\/api\/accounts\/(\d+)\/deposits$/))) {
      const r = replay(idempotencyKey); if (r) return { status: 200, body: r };
      const a = findAccount(m[1]);
      if (!a || a.system) throw this._err(404, 'NOT_FOUND', 'Account not found.');
      if (!ownedOrAdmin(a)) throw this._err(403, 'ACCESS_DENIED', 'This account is not yours.');
      if (a.status !== 'ACTIVE') throw this._err(409, 'ACCOUNT_NOT_ACTIVE', 'This account is not active.');
      const cents = parseAmount(body?.amount);
      if (cents <= 0) throw this._err(422, 'VALIDATION_ERROR', 'Enter an amount greater than zero.');
      this._maybeConflict(idempotencyKey, cents);
      this._postMovement(this.system, 'DEBIT', cents, 'Cash in');
      const e = this._postMovement(a, 'CREDIT', cents, 'Deposit');
      return { status: 201, body: store(idempotencyKey, { transaction: e, balance: formatCents(a.balance), accountId: a.id }) };
    }

    // POST /api/accounts/{id}/withdrawals
    if (method === 'POST' && (m = path.match(/^\/api\/accounts\/(\d+)\/withdrawals$/))) {
      const r = replay(idempotencyKey); if (r) return { status: 200, body: r };
      const a = findAccount(m[1]);
      if (!a || a.system) throw this._err(404, 'NOT_FOUND', 'Account not found.');
      if (!ownedOrAdmin(a)) throw this._err(403, 'ACCESS_DENIED', 'This account is not yours.');
      if (a.status !== 'ACTIVE') throw this._err(409, 'ACCOUNT_NOT_ACTIVE', 'This account is not active.');
      const cents = parseAmount(body?.amount);
      if (cents <= 0) throw this._err(422, 'VALIDATION_ERROR', 'Enter an amount greater than zero.');
      if (cents > a.balance) throw this._err(422, 'INSUFFICIENT_FUNDS', 'Insufficient funds.');
      this._maybeConflict(idempotencyKey, cents);
      const e = this._postMovement(a, 'DEBIT', cents, 'Withdrawal');
      this._postMovement(this.system, 'CREDIT', cents, 'Cash out');
      return { status: 201, body: store(idempotencyKey, { transaction: e, balance: formatCents(a.balance), accountId: a.id }) };
    }

    // POST /api/transfers
    if (method === 'POST' && path === '/api/transfers') {
      const r = replay(idempotencyKey); if (r) return { status: 200, body: r };
      const { sourceAccountId, destinationAccountId, amount } = body || {};
      const src = findAccount(sourceAccountId);
      const dst = findAccount(destinationAccountId);
      if (!src || src.system) throw this._err(404, 'NOT_FOUND', 'Source account not found.');
      if (!ownedOrAdmin(src)) throw this._err(403, 'ACCESS_DENIED', 'The source account is not yours.');
      if (!dst || dst.system) throw this._err(404, 'NOT_FOUND', 'Destination account not found.');
      if (src.id === dst.id) throw this._err(422, 'VALIDATION_ERROR', 'Source and destination must differ.');
      if (src.status !== 'ACTIVE') throw this._err(409, 'ACCOUNT_NOT_ACTIVE', 'The source account is not active.');
      if (dst.status !== 'ACTIVE') throw this._err(409, 'ACCOUNT_NOT_ACTIVE', 'The destination account is not active.');
      const cents = parseAmount(amount);
      if (cents <= 0) throw this._err(422, 'VALIDATION_ERROR', 'Enter an amount greater than zero.');
      if (cents > src.balance) throw this._err(422, 'INSUFFICIENT_FUNDS', 'Insufficient funds.');
      this._maybeConflict(idempotencyKey, cents);
      const out = this._postMovement(src, 'DEBIT', cents, 'Transfer to account ' + dst.accountNumber);
      this._postMovement(dst, 'CREDIT', cents, 'Transfer from account ' + src.accountNumber);
      return { status: 201, body: store(idempotencyKey, { transaction: out, balance: formatCents(src.balance), accountId: src.id }) };
    }

    // ---------- admin ----------
    if (path.startsWith('/api/admin/')) {
      if (!isAdmin) throw this._err(403, 'ACCESS_DENIED', 'Administrator access required.');
      if (method === 'GET' && (m = path.match(/^\/api\/admin\/accounts\/(\d+)$/))) {
        const a = findAccount(m[1]);
        if (!a || a.system) throw this._err(404, 'NOT_FOUND', 'Account not found.');
        const owner = this.customers.find(c => c.id === a.customerId);
        return { status: 200, body: { ...this._publicAccount(a), ownerName: owner?.fullName, ownerEmail: owner?.email } };
      }
      if (method === 'POST' && (m = path.match(/^\/api\/admin\/accounts\/(\d+)\/freeze$/))) {
        const a = findAccount(m[1]);
        if (!a || a.system) throw this._err(404, 'NOT_FOUND', 'Account not found.');
        if (a.status === 'CLOSED') throw this._err(409, 'ACCOUNT_NOT_ACTIVE', 'A closed account cannot be frozen.');
        a.status = a.status === 'FROZEN' ? 'ACTIVE' : 'FROZEN';
        return { status: 200, body: this._publicAccount(a) };
      }
      if (method === 'POST' && (m = path.match(/^\/api\/admin\/accounts\/(\d+)\/close$/))) {
        const a = findAccount(m[1]);
        if (!a || a.system) throw this._err(404, 'NOT_FOUND', 'Account not found.');
        a.status = 'CLOSED';
        return { status: 200, body: this._publicAccount(a) };
      }
    }

    throw this._err(404, 'NOT_FOUND', 'No such endpoint.');
  }
}
