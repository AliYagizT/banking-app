// REST client for the Spring backend. Every call carries the Firebase ID token as
// `Authorization: Bearer <token>`, which the backend verifies. Money movements also carry
// an `Idempotency-Key` so a retried request never double-charges.

import { API_BASE_URL } from "./config.js";
import { idToken } from "./firebase.js";

const ERROR_MESSAGES = {
  UNAUTHENTICATED: "Oturum gerekli. Lütfen tekrar giriş yapın.",
  ACCESS_DENIED: "Bu işlem için yetkiniz yok.",
  NOT_FOUND: "Kayıt bulunamadı.",
  VALIDATION_ERROR: "Lütfen bilgileri kontrol edin.",
  INSUFFICIENT_FUNDS: "Yetersiz bakiye.",
  ACCOUNT_NOT_ACTIVE: "Hesap aktif değil, işlem yapılamaz.",
  CONCURRENCY_CONFLICT: "Hesap aynı anda güncellendi, tekrar deneyin.",
  NETWORK: "Sunucuya ulaşılamıyor. Backend çalışıyor mu?",
  UNKNOWN: "Beklenmeyen bir hata oluştu.",
};

export class ApiError extends Error {
  constructor(code, message, status, fieldErrors) {
    super(message || ERROR_MESSAGES[code] || ERROR_MESSAGES.UNKNOWN);
    this.code = code || "UNKNOWN";
    this.status = status;
    this.fieldErrors = fieldErrors || [];
    this.friendly =
      (code === "VALIDATION_ERROR" && message) ? message
        : (ERROR_MESSAGES[this.code] || ERROR_MESSAGES.UNKNOWN);
  }
}

const uuid = () =>
  (crypto.randomUUID ? crypto.randomUUID()
    : "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, (c) => {
        const r = (Math.random() * 16) | 0;
        return (c === "x" ? r : (r & 0x3) | 0x8).toString(16);
      }));

async function request(method, path, { body, idempotent } = {}) {
  const token = await idToken();
  const headers = { Accept: "application/json" };
  if (token) headers["Authorization"] = "Bearer " + token;
  if (body) headers["Content-Type"] = "application/json";
  if (idempotent) headers["Idempotency-Key"] = uuid();

  let res;
  try {
    res = await fetch(API_BASE_URL + path, {
      method,
      headers,
      body: body ? JSON.stringify(body) : undefined,
    });
  } catch (netErr) {
    throw new ApiError("NETWORK", ERROR_MESSAGES.NETWORK, 0);
  }

  let data = null;
  try {
    data = await res.json();
  } catch (_) {
    /* empty body (e.g. 201 with no content) */
  }
  if (!res.ok) {
    const statusCode =
      res.status === 401 ? "UNAUTHENTICATED" : res.status === 403 ? "ACCESS_DENIED" : null;
    const code = (data && data.code) || statusCode || "UNKNOWN";
    throw new ApiError(code, data && data.message, res.status, data && data.fieldErrors);
  }
  return data;
}

export const api = {
  // ---- customer profile ----
  registerProfile: (fullName) => request("POST", "/api/customers", { body: { fullName } }),
  me: () => request("GET", "/api/customers/me"),

  // ---- accounts ----
  listAccounts: () => request("GET", "/api/accounts"),
  openAccount: (currency = "USD") => request("POST", "/api/accounts", { body: { currency } }),
  getBalance: (id) => request("GET", `/api/accounts/${id}/balance`),
  getTransactions: (id, page = 0, size = 20) =>
    request("GET", `/api/accounts/${id}/transactions?page=${page}&size=${size}`),
  deposit: (id, amount) => request("POST", `/api/accounts/${id}/deposits`, { body: { amount }, idempotent: true }),
  withdraw: (id, amount) => request("POST", `/api/accounts/${id}/withdrawals`, { body: { amount }, idempotent: true }),
  transfer: (sourceAccountId, destinationAccountId, amount) =>
    request("POST", "/api/transfers", { body: { sourceAccountId, destinationAccountId, amount }, idempotent: true }),

  // ---- credit (customer) ----
  listProducts: () => request("GET", "/api/credit-products"),
  submitApplication: (payload) => request("POST", "/api/credit-applications", { body: payload }),
  listApplications: () => request("GET", "/api/credit-applications"),
  getApplication: (id) => request("GET", `/api/credit-applications/${id}`),
  getRepaymentPlan: (id) => request("GET", `/api/credit-applications/${id}/repayment-plan`),

  // ---- credit (banker) ----
  bankerQueue: () => request("GET", "/api/banker/credit-applications"),
  bankerDetail: (id) => request("GET", `/api/banker/credit-applications/${id}`),
  bankerApprove: (id, reason) => request("POST", `/api/banker/credit-applications/${id}/approve`, { body: { reason } }),
  bankerReject: (id, reason) => request("POST", `/api/banker/credit-applications/${id}/reject`, { body: { reason } }),

  // ---- admin ----
  adminGetAccount: (id) => request("GET", `/api/admin/accounts/${id}`),
  adminFreeze: (id) => request("POST", `/api/admin/accounts/${id}/freeze`),
  adminClose: (id) => request("POST", `/api/admin/accounts/${id}/close`),
  adminUsers: () => request("GET", "/api/admin/users"),
  adminAddBanker: (payload) => request("POST", "/api/admin/bankers", { body: payload }),
  adminOperations: (limit = 100) => request("GET", `/api/admin/operations?limit=${limit}`),
};
