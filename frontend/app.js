// The banking SPA. Build-less: React + htm loaded from a CDN as ES modules, no bundler.
// Auth is Firebase (email/password); every backend call carries the Firebase ID token.

import React from "https://esm.sh/react@18.3.1";
import { createRoot } from "https://esm.sh/react-dom@18.3.1/client";
import htm from "https://esm.sh/htm@3.1.1";
import { onAuth, signIn, signUp, signOut, friendlyAuthError } from "./firebase.js";
import { api } from "./api.js";

const html = htm.bind(React.createElement);
const { useState, useEffect, useCallback } = React;

const money = (n) =>
  Number(n).toLocaleString("tr-TR", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
const pct = (n) => (Number(n) * 100).toFixed(1) + "%";
const date = (iso) => (iso ? new Date(iso).toLocaleString("tr-TR") : "—");

const OP_LABELS = {
  DEPOSIT: "Para yatırma",
  WITHDRAWAL: "Para çekme",
  TRANSFER: "Transfer",
  CREDIT_DISBURSEMENT: "Kredi tahsisi",
};

// ---------- toasts ----------
function useToasts() {
  const [toasts, setToasts] = useState([]);
  const notify = useCallback((message, kind = "info") => {
    const id = Math.random().toString(36).slice(2);
    setToasts((t) => [...t, { id, message, kind }]);
    setTimeout(() => setToasts((t) => t.filter((x) => x.id !== id)), 4500);
  }, []);
  return { toasts, notify };
}

function Toasts({ toasts }) {
  return html`<div className="toasts">
    ${toasts.map((t) => html`<div key=${t.id} className=${"toast toast-" + t.kind}>${t.message}</div>`)}
  </div>`;
}

// ---------- auth screen ----------
function AuthScreen({ notify }) {
  const [mode, setMode] = useState("login");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [busy, setBusy] = useState(false);

  async function submit(e) {
    e.preventDefault();
    setBusy(true);
    try {
      if (mode === "login") await signIn(email.trim(), password);
      else await signUp(email.trim(), password);
      // onAuth listener takes over from here.
    } catch (err) {
      notify(friendlyAuthError(err.code), "error");
    } finally {
      setBusy(false);
    }
  }

  return html`<div className="auth-wrap">
    <div className="card auth-card">
      <div className="brand"><span className="logo">₺</span> Softtech Bank</div>
      <p className="muted">Firebase ile güvenli giriş</p>
      <form onSubmit=${submit}>
        <label>E-posta
          <input type="email" required value=${email} onChange=${(e) => setEmail(e.target.value)} placeholder="ornek@eposta.com" />
        </label>
        <label>Şifre
          <input type="password" required minLength=${6} value=${password} onChange=${(e) => setPassword(e.target.value)} placeholder="••••••" />
        </label>
        <button className="btn primary" disabled=${busy} type="submit">
          ${busy ? "..." : mode === "login" ? "Giriş yap" : "Kayıt ol"}
        </button>
      </form>
      <div className="switch">
        ${mode === "login"
          ? html`Hesabın yok mu? <a onClick=${() => setMode("signup")}>Kayıt ol</a>`
          : html`Zaten hesabın var mı? <a onClick=${() => setMode("login")}>Giriş yap</a>`}
      </div>
      <p className="hint">Bankacı demo: <code>banker1@bank.local</code> / <code>banker123</code></p>
    </div>
  </div>`;
}

// ---------- complete profile (prospect) ----------
function CompleteProfile({ email, onDone, notify }) {
  const [fullName, setFullName] = useState("");
  const [busy, setBusy] = useState(false);
  async function submit(e) {
    e.preventDefault();
    setBusy(true);
    try {
      await api.registerProfile(fullName.trim());
      notify("Profil oluşturuldu, hoş geldiniz!", "success");
      onDone();
    } catch (err) {
      notify(err.friendly, "error");
      setBusy(false);
    }
  }
  return html`<div className="auth-wrap">
    <div className="card auth-card">
      <h2>Profilini tamamla</h2>
      <p className="muted">${email} için hesap oluşturuluyor.</p>
      <form onSubmit=${submit}>
        <label>Ad Soyad
          <input required value=${fullName} onChange=${(e) => setFullName(e.target.value)} placeholder="Ali Yağız" />
        </label>
        <button className="btn primary" disabled=${busy} type="submit">${busy ? "..." : "Devam et"}</button>
      </form>
    </div>
  </div>`;
}

// ---------- accounts ----------
function AccountsPanel({ notify }) {
  const [accounts, setAccounts] = useState(null);
  const [selected, setSelected] = useState(null);

  const load = useCallback(async () => {
    try {
      const res = await api.listAccounts();
      setAccounts(res.accounts || res.content || res || []);
    } catch (e) {
      notify(e.friendly, "error");
    }
  }, [notify]);
  useEffect(() => { load(); }, [load]);

  async function open() {
    try {
      await api.openAccount("USD");
      notify("Hesap açıldı.", "success");
      load();
    } catch (e) { notify(e.friendly, "error"); }
  }

  if (!accounts) return html`<${Loading} />`;
  if (selected) return html`<${AccountDetail} account=${selected} onBack=${() => { setSelected(null); load(); }} notify=${notify} />`;

  return html`<div>
    <div className="row-between">
      <h2>Hesaplarım</h2>
      <button className="btn primary" onClick=${open}>+ Yeni hesap (USD)</button>
    </div>
    ${accounts.length === 0
      ? html`<p className="muted">Henüz hesabın yok. Yeni bir hesap aç.</p>`
      : html`<div className="grid">
          ${accounts.map((a) => html`<div key=${a.id} className="card acct" onClick=${() => setSelected(a)}>
            <div className="acct-no">${a.accountNumber}</div>
            <div className="acct-bal">${money(a.balance)} <span className="cur">${a.currency}</span></div>
            <div className=${"pill pill-" + a.status.toLowerCase()}>${a.status}</div>
          </div>`)}
        </div>`}
  </div>`;
}

function AccountDetail({ account, onBack, notify }) {
  const [balance, setBalance] = useState(account.balance);
  const [txns, setTxns] = useState(null);
  const [modal, setModal] = useState(null); // 'deposit' | 'withdraw' | 'transfer'

  const load = useCallback(async () => {
    try {
      const [b, t] = await Promise.all([api.getBalance(account.id), api.getTransactions(account.id, 0, 25)]);
      setBalance(b.balance);
      setTxns(t.content || []);
    } catch (e) { notify(e.friendly, "error"); }
  }, [account.id, notify]);
  useEffect(() => { load(); }, [load]);

  return html`<div>
    <a className="back" onClick=${onBack}>← Hesaplara dön</a>
    <div className="card">
      <div className="acct-no">${account.accountNumber}</div>
      <div className="acct-bal big">${money(balance)} <span className="cur">${account.currency}</span></div>
      <div className="actions">
        <button className="btn" onClick=${() => setModal("deposit")}>Para yatır</button>
        <button className="btn" onClick=${() => setModal("withdraw")}>Para çek</button>
        <button className="btn" onClick=${() => setModal("transfer")}>Transfer</button>
      </div>
    </div>
    <h3>İşlem geçmişi</h3>
    ${!txns ? html`<${Loading} />`
      : txns.length === 0 ? html`<p className="muted">Henüz işlem yok.</p>`
      : html`<table className="tbl">
          <thead><tr><th>Tarih</th><th>İşlem</th><th className="r">Tutar</th><th className="r">Bakiye</th></tr></thead>
          <tbody>${txns.map((t) => html`<tr key=${t.entryId}>
            <td>${date(t.timestamp)}</td>
            <td>${OP_LABELS[t.type] || t.type}</td>
            <td className=${"r " + (t.direction === "CREDIT" ? "credit" : "debit")}>
              ${t.direction === "CREDIT" ? "+" : "−"}${money(t.amount)}
            </td>
            <td className="r">${money(t.balanceAfter)}</td>
          </tr>`)}</tbody>
        </table>`}
    ${modal && html`<${MovementModal} kind=${modal} account=${account} onClose=${() => setModal(null)}
      onDone=${() => { setModal(null); load(); }} notify=${notify} />`}
  </div>`;
}

function MovementModal({ kind, account, onClose, onDone, notify }) {
  const [amount, setAmount] = useState("");
  const [dest, setDest] = useState("");
  const [busy, setBusy] = useState(false);
  const titles = { deposit: "Para yatır", withdraw: "Para çek", transfer: "Transfer" };

  async function submit(e) {
    e.preventDefault();
    setBusy(true);
    try {
      const amt = Number(amount);
      if (kind === "deposit") await api.deposit(account.id, amt);
      else if (kind === "withdraw") await api.withdraw(account.id, amt);
      else await api.transfer(account.id, Number(dest), amt);
      notify("İşlem başarılı.", "success");
      onDone();
    } catch (err) {
      notify(err.friendly, "error");
      setBusy(false);
    }
  }

  return html`<${Modal} title=${titles[kind]} onClose=${onClose}>
    <form onSubmit=${submit}>
      ${kind === "transfer" && html`<label>Hedef hesap ID
        <input required type="number" value=${dest} onChange=${(e) => setDest(e.target.value)} />
      </label>`}
      <label>Tutar
        <input required type="number" step="0.01" min="0.01" value=${amount} onChange=${(e) => setAmount(e.target.value)} />
      </label>
      <button className="btn primary" disabled=${busy} type="submit">${busy ? "..." : "Onayla"}</button>
    </form>
  </${Modal}>`;
}

// ---------- credit (customer) ----------
function CreditPanel({ notify }) {
  const [products, setProducts] = useState(null);
  const [accounts, setAccounts] = useState([]);
  const [apps, setApps] = useState([]);
  const [plan, setPlan] = useState(null);

  const load = useCallback(async () => {
    try {
      const [p, acc, a] = await Promise.all([api.listProducts(), api.listAccounts(), api.listApplications()]);
      setProducts(p);
      setAccounts(acc.accounts || acc.content || acc || []);
      setApps(a);
    } catch (e) { notify(e.friendly, "error"); }
  }, [notify]);
  useEffect(() => { load(); }, [load]);

  async function showPlan(id) {
    try { setPlan(await api.getRepaymentPlan(id)); }
    catch (e) { notify(e.friendly, "error"); }
  }

  if (!products) return html`<${Loading} />`;
  return html`<div>
    <h2>Kredi</h2>
    <${CreditForm} products=${products} accounts=${accounts} notify=${notify} onDone=${load} />
    <h3>Başvurularım</h3>
    ${apps.length === 0 ? html`<p className="muted">Henüz başvurun yok.</p>`
      : html`<table className="tbl">
        <thead><tr><th>#</th><th>Ürün</th><th className="r">Tutar</th><th className="r">Taksit</th><th>Vade</th><th>Durum</th><th></th></tr></thead>
        <tbody>${apps.map((a) => html`<tr key=${a.id}>
          <td>${a.id}</td><td>${a.productCode}</td>
          <td className="r">${money(a.amount)}</td>
          <td className="r">${a.monthlyInstallment ? money(a.monthlyInstallment) : "—"}</td>
          <td>${a.termMonths} ay</td>
          <td><span className=${"pill pill-" + a.status.toLowerCase()}>${statusLabel(a.status)}</span></td>
          <td><a onClick=${() => showPlan(a.id)}>Ödeme planı</a></td>
        </tr>`)}</tbody></table>`}
    ${plan && html`<${RepaymentPlanModal} plan=${plan} onClose=${() => setPlan(null)} />`}
  </div>`;
}

function CreditForm({ products, accounts, notify, onDone }) {
  const [f, setF] = useState({
    productCode: products[0] ? products[0].code : "", amount: "", termMonths: 12,
    monthlyIncome: "", profession: "", employmentMonths: "",
    disbursementAccountId: accounts[0] ? String(accounts[0].id) : "",
  });
  const [busy, setBusy] = useState(false);
  const set = (k) => (e) => setF({ ...f, [k]: e.target.value });

  async function submit(e) {
    e.preventDefault();
    if (!f.disbursementAccountId) { notify("Önce bir hesap açmalısın.", "error"); return; }
    setBusy(true);
    try {
      await api.submitApplication({
        productCode: f.productCode,
        amount: Number(f.amount),
        termMonths: Number(f.termMonths),
        monthlyIncome: Number(f.monthlyIncome),
        profession: f.profession,
        employmentMonths: Number(f.employmentMonths),
        disbursementAccountId: Number(f.disbursementAccountId),
      });
      notify("Başvurun alındı, bankacıya iletildi.", "success");
      setF({ ...f, amount: "", monthlyIncome: "", profession: "", employmentMonths: "" });
      onDone();
    } catch (err) { notify(err.friendly, "error"); }
    finally { setBusy(false); }
  }

  return html`<form className="card credit-form" onSubmit=${submit}>
    <div className="form-grid">
      <label>Kredi ürünü
        <select value=${f.productCode} onChange=${set("productCode")}>
          ${products.map((p) => html`<option key=${p.code} value=${p.code}>${p.name} (%${(p.annualInterestRate * 100).toFixed(1)})</option>`)}
        </select>
      </label>
      <label>Tutar
        <input required type="number" step="0.01" min="1" value=${f.amount} onChange=${set("amount")} />
      </label>
      <label>Vade (ay)
        <input required type="number" min="1" max="60" value=${f.termMonths} onChange=${set("termMonths")} />
      </label>
      <label>Aylık gelir
        <input required type="number" step="0.01" min="1" value=${f.monthlyIncome} onChange=${set("monthlyIncome")} />
      </label>
      <label>Meslek
        <input required value=${f.profession} onChange=${set("profession")} placeholder="Yazılım Mühendisi" />
      </label>
      <label>Çalışma süresi (ay)
        <input required type="number" min="0" value=${f.employmentMonths} onChange=${set("employmentMonths")} />
      </label>
      <label>Yatırılacak hesap
        <select value=${f.disbursementAccountId} onChange=${set("disbursementAccountId")}>
          ${accounts.map((a) => html`<option key=${a.id} value=${a.id}>${a.accountNumber}</option>`)}
        </select>
      </label>
    </div>
    <button className="btn primary" disabled=${busy} type="submit">${busy ? "..." : "Başvur"}</button>
  </form>`;
}

function RepaymentPlanModal({ plan, onClose }) {
  return html`<${Modal} title="Geri ödeme planı" onClose=${onClose} wide=${true}>
    <div className="plan-summary">
      <div><span>Aylık taksit</span><strong>${money(plan.monthlyInstallment)}</strong></div>
      <div><span>Toplam ödeme</span><strong>${money(plan.totalPayment)}</strong></div>
      <div><span>Toplam faiz</span><strong>${money(plan.totalInterest)}</strong></div>
    </div>
    <table className="tbl">
      <thead><tr><th>#</th><th>Vade</th><th className="r">Taksit</th><th className="r">Anapara</th><th className="r">Faiz</th><th className="r">Kalan</th></tr></thead>
      <tbody>${plan.installments.map((i) => html`<tr key=${i.number}>
        <td>${i.number}</td><td>${i.dueDate}</td>
        <td className="r">${money(i.totalPayment)}</td>
        <td className="r">${money(i.principalPortion)}</td>
        <td className="r">${money(i.interestPortion)}</td>
        <td className="r">${money(i.remainingPrincipal)}</td>
      </tr>`)}</tbody>
    </table>
  </${Modal}>`;
}

// ---------- banker ----------
function BankerPanel({ notify }) {
  const [queue, setQueue] = useState(null);
  const [detail, setDetail] = useState(null);

  const load = useCallback(async () => {
    try { setQueue(await api.bankerQueue()); }
    catch (e) { notify(e.friendly, "error"); }
  }, [notify]);
  useEffect(() => { load(); }, [load]);

  async function openDetail(id) {
    try { setDetail(await api.bankerDetail(id)); }
    catch (e) { notify(e.friendly, "error"); }
  }
  async function decide(id, kind, reason) {
    try {
      if (kind === "approve") await api.bankerApprove(id, reason);
      else await api.bankerReject(id, reason);
      notify(kind === "approve" ? "Onaylandı ve tahsis edildi." : "Reddedildi.", "success");
      setDetail(null); load();
    } catch (e) { notify(e.friendly, "error"); }
  }

  if (detail) return html`<${BankerDetail} data=${detail} onBack=${() => setDetail(null)} onDecide=${decide} />`;
  if (!queue) return html`<${Loading} />`;
  return html`<div>
    <h2>Bankacı — Değerlendirme kuyruğu</h2>
    ${queue.length === 0 ? html`<p className="muted">Bekleyen başvuru yok.</p>`
      : html`<table className="tbl">
        <thead><tr><th>#</th><th>Müşteri</th><th>Ürün</th><th className="r">Tutar</th><th className="r">Gelir</th><th>Vade</th><th></th></tr></thead>
        <tbody>${queue.map((a) => html`<tr key=${a.id}>
          <td>${a.id}</td><td>#${a.customerId}</td><td>${a.productCode}</td>
          <td className="r">${money(a.amount)}</td><td className="r">${money(a.monthlyIncome)}</td>
          <td>${a.termMonths} ay</td>
          <td><button className="btn small" onClick=${() => openDetail(a.id)}>İncele</button></td>
        </tr>`)}</tbody></table>`}
  </div>`;
}

function Criterion({ ok, label }) {
  return html`<div className=${"crit " + (ok ? "ok" : "bad")}>${ok ? "✓" : "✗"} ${label}</div>`;
}

function BankerDetail({ data, onBack, onDecide }) {
  const a = data.application, s = data.assessment;
  const [reason, setReason] = useState("");
  return html`<div>
    <a className="back" onClick=${onBack}>← Kuyruğa dön</a>
    <div className="two-col">
      <div className="card">
        <h3>Başvuru #${a.id}</h3>
        <dl className="kv">
          <dt>Müşteri</dt><dd>#${a.customerId}</dd>
          <dt>Ürün</dt><dd>${a.productCode}</dd>
          <dt>Tutar</dt><dd>${money(a.amount)}</dd>
          <dt>Vade</dt><dd>${a.termMonths} ay</dd>
          <dt>Yıllık faiz</dt><dd>${pct(a.annualInterestRate)}</dd>
          <dt>Aylık gelir</dt><dd>${money(a.monthlyIncome)}</dd>
          <dt>Meslek</dt><dd>${a.profession}</dd>
          <dt>Çalışma süresi</dt><dd>${a.employmentMonths} ay</dd>
        </dl>
      </div>
      <div className="card">
        <h3>Underwriting kriterleri</h3>
        <div className="metrics">
          <div><span>Aylık taksit</span><strong>${money(s.monthlyInstallment)}</strong></div>
          <div><span>Taksit / Gelir</span><strong>${pct(s.installmentToIncome)}</strong></div>
          <div><span>Toplam ödeme</span><strong>${money(s.totalPayment)}</strong></div>
          <div><span>Toplam faiz</span><strong>${money(s.totalInterest)}</strong></div>
        </div>
        <${Criterion} ok=${s.withinIncomeLimit} label=${"Taksit/gelir ≤ %" + (s.maxInstallmentToIncome * 100)} />
        <${Criterion} ok=${s.meetsEmploymentMinimum} label=${"Çalışma süresi ≥ " + s.minEmploymentMonths + " ay"} />
        <${Criterion} ok=${s.withinProductLimits} label="Ürün limitleri içinde" />
        <${Criterion} ok=${s.customerActive} label="Müşteri aktif" />
        <div className=${"verdict " + (s.meetsAllCriteria ? "ok" : "bad")}>
          ${s.meetsAllCriteria ? "Tüm kriterler uygun" : "Bazı kriterler karşılanmıyor"}
        </div>
      </div>
    </div>
    <div className="card decision">
      <label>Gerekçe (opsiyonel)
        <input value=${reason} onChange=${(e) => setReason(e.target.value)} placeholder="Karar notu" />
      </label>
      <div className="actions">
        <button className="btn success" onClick=${() => onDecide(a.id, "approve", reason)}>Onayla & tahsis et</button>
        <button className="btn danger" onClick=${() => onDecide(a.id, "reject", reason)}>Reddet</button>
      </div>
    </div>
  </div>`;
}

// ---------- admin ----------
function AdminPanel({ notify }) {
  const sections = [["users", "Kullanıcılar"], ["logs", "İşlem logları"], ["accounts", "Hesaplar"]];
  const [section, setSection] = useState("users");
  return html`<div>
    <h2>Admin paneli</h2>
    <div className="subtabs">
      ${sections.map(([k, label]) => html`<button key=${k}
        className=${"subtab" + (section === k ? " active" : "")} onClick=${() => setSection(k)}>${label}</button>`)}
    </div>
    ${section === "users" && html`<${AdminUsers} notify=${notify} />`}
    ${section === "logs" && html`<${AdminLogs} notify=${notify} />`}
    ${section === "accounts" && html`<${AdminAccounts} notify=${notify} />`}
  </div>`;
}

function AdminUsers({ notify }) {
  const [users, setUsers] = useState(null);
  const load = useCallback(async () => {
    try { setUsers(await api.adminUsers()); }
    catch (e) { notify(e.friendly, "error"); }
  }, [notify]);
  useEffect(() => { load(); }, [load]);

  return html`<div>
    <${AddBankerForm} notify=${notify} onDone=${load} />
    <h3>Tüm kullanıcılar ${users ? html`<span className="muted">(${users.length})</span>` : ""}</h3>
    ${!users ? html`<${Loading} />`
      : html`<table className="tbl">
        <thead><tr><th>#</th><th>Ad Soyad</th><th>E-posta</th><th>Rol</th><th>Durum</th><th>Kayıt</th></tr></thead>
        <tbody>${users.map((u) => html`<tr key=${u.id}>
          <td>${u.id}</td><td>${u.fullName}</td><td>${u.email}</td>
          <td><span className=${"role role-" + u.role.toLowerCase()}>${u.role}</span></td>
          <td><span className=${"pill pill-" + u.status.toLowerCase()}>${u.status}</span></td>
          <td>${date(u.createdAt)}</td>
        </tr>`)}</tbody></table>`}
  </div>`;
}

function AddBankerForm({ notify, onDone }) {
  const [f, setF] = useState({ fullName: "", email: "", password: "" });
  const [busy, setBusy] = useState(false);
  const set = (k) => (e) => setF({ ...f, [k]: e.target.value });
  async function submit(e) {
    e.preventDefault();
    setBusy(true);
    try {
      await api.adminAddBanker({ fullName: f.fullName.trim(), email: f.email.trim(), password: f.password });
      notify("Bankacı eklendi (Firebase hesabı oluşturuldu).", "success");
      setF({ fullName: "", email: "", password: "" });
      onDone();
    } catch (err) { notify(err.friendly, "error"); }
    finally { setBusy(false); }
  }
  return html`<form className="card" onSubmit=${submit}>
    <h3 style=${{ marginTop: 0 }}>Bankacı ekle</h3>
    <div className="form-grid">
      <label>Ad Soyad<input required value=${f.fullName} onChange=${set("fullName")} placeholder="Ayşe Yılmaz" /></label>
      <label>E-posta<input required type="email" value=${f.email} onChange=${set("email")} placeholder="bankaci@bank.local" /></label>
      <label>Şifre<input required type="password" minLength=${6} value=${f.password} onChange=${set("password")} placeholder="En az 6 karakter" /></label>
    </div>
    <button className="btn primary" disabled=${busy} type="submit">${busy ? "..." : "Bankacı oluştur"}</button>
  </form>`;
}

function AdminLogs({ notify }) {
  const [logs, setLogs] = useState(null);
  const load = useCallback(async () => {
    try { setLogs(await api.adminOperations(200)); }
    catch (e) { notify(e.friendly, "error"); }
  }, [notify]);
  useEffect(() => { load(); }, [load]);

  return html`<div>
    <div className="row-between">
      <h3 style=${{ margin: 0 }}>İşlem / audit logları ${logs ? html`<span className="muted">(${logs.length})</span>` : ""}</h3>
      <button className="btn small" onClick=${load}>Yenile</button>
    </div>
    ${!logs ? html`<${Loading} />`
      : logs.length === 0 ? html`<p className="muted">Henüz işlem kaydı yok.</p>`
      : html`<table className="tbl">
        <thead><tr><th>#</th><th>Tarih</th><th>İşlem</th><th className="r">Ana hesap</th><th className="r">Karşı hesap</th><th className="r">Tutar</th></tr></thead>
        <tbody>${logs.map((l) => html`<tr key=${l.id}>
          <td>${l.id}</td><td>${date(l.createdAt)}</td>
          <td>${OP_LABELS[l.type] || l.type}</td>
          <td className="r">#${l.primaryAccountId}</td>
          <td className="r">${l.counterAccountId != null ? "#" + l.counterAccountId : "—"}</td>
          <td className="r">${money(l.amount)}</td>
        </tr>`)}</tbody></table>`}
  </div>`;
}

function AdminAccounts({ notify }) {
  const [id, setId] = useState("");
  const [account, setAccount] = useState(null);

  async function lookup(e) {
    e.preventDefault();
    try { setAccount(await api.adminGetAccount(Number(id))); }
    catch (err) { setAccount(null); notify(err.friendly, "error"); }
  }
  async function act(kind) {
    try {
      const res = kind === "freeze" ? await api.adminFreeze(account.id) : await api.adminClose(account.id);
      setAccount(res);
      notify(kind === "freeze" ? "Hesap donduruldu." : "Hesap kapatıldı.", "success");
    } catch (e) { notify(e.friendly, "error"); }
  }

  return html`<div>
    <form className="row" onSubmit=${lookup}>
      <input type="number" placeholder="Hesap ID" value=${id} onChange=${(e) => setId(e.target.value)} />
      <button className="btn primary" type="submit">Getir</button>
    </form>
    ${account && html`<div className="card">
      <div className="acct-no">${account.accountNumber}</div>
      <div className="acct-bal">${money(account.balance)} <span className="cur">${account.currency}</span></div>
      <div>Sahip müşteri: #${account.customerId}</div>
      <div className=${"pill pill-" + account.status.toLowerCase()}>${account.status}</div>
      <div className="actions">
        <button className="btn" onClick=${() => act("freeze")} disabled=${account.status !== "ACTIVE"}>Dondur</button>
        <button className="btn danger" onClick=${() => act("close")} disabled=${account.status === "CLOSED"}>Kapat</button>
      </div>
    </div>`}
  </div>`;
}

// ---------- shared ----------
function Loading() { return html`<div className="loading">Yükleniyor…</div>`; }

function Modal({ title, onClose, children, wide }) {
  return html`<div className="overlay" onClick=${onClose}>
    <div className=${"modal" + (wide ? " wide" : "")} onClick=${(e) => e.stopPropagation()}>
      <div className="modal-head"><h3>${title}</h3><button className="x" onClick=${onClose}>×</button></div>
      <div className="modal-body">${children}</div>
    </div>
  </div>`;
}

function statusLabel(s) {
  return { SUBMITTED: "Beklemede", APPROVED: "Onaylandı", REJECTED: "Reddedildi" }[s] || s;
}

// ---------- dashboard ----------
function Dashboard({ profile, notify }) {
  const role = profile.role;
  // Staff (banker/admin) are not customers: they don't see accounts or credit application.
  const tabs =
    role === "ADMIN" ? [["admin", "Admin"]]
      : role === "BANKER" ? [["banker", "Bankacı"]]
      : [["accounts", "Hesaplarım"], ["credit", "Kredi"]];
  const [tab, setTab] = useState(tabs[0][0]);

  return html`<div className="app">
    <header className="topbar">
      <div className="brand"><span className="logo">₺</span> Softtech Bank</div>
      <nav className="tabs">
        ${tabs.map(([k, label]) => html`<button key=${k} className=${"tab" + (tab === k ? " active" : "")} onClick=${() => setTab(k)}>${label}</button>`)}
      </nav>
      <div className="user">
        <span className=${"role role-" + role.toLowerCase()}>${role}</span>
        <span className="email">${profile.email}</span>
        <button className="btn small" onClick=${() => signOut()}>Çıkış</button>
      </div>
    </header>
    <main className="content">
      ${tab === "accounts" && html`<${AccountsPanel} notify=${notify} />`}
      ${tab === "credit" && html`<${CreditPanel} notify=${notify} />`}
      ${tab === "banker" && html`<${BankerPanel} notify=${notify} />`}
      ${tab === "admin" && html`<${AdminPanel} notify=${notify} />`}
    </main>
  </div>`;
}

// ---------- root ----------
function App() {
  const [user, setUser] = useState(undefined); // undefined=loading, null=logged out
  const [profile, setProfile] = useState(undefined); // undefined=loading, null=needs registration
  const { toasts, notify } = useToasts();

  useEffect(() => onAuth((u) => setUser(u || null)), []);

  const loadProfile = useCallback(async () => {
    setProfile(undefined);
    try {
      setProfile(await api.me());
    } catch (e) {
      if (e.status === 403 || e.status === 404) setProfile(null);
      else { notify(e.friendly, "error"); setProfile(null); }
    }
  }, [notify]);

  useEffect(() => { if (user) loadProfile(); else setProfile(undefined); }, [user, loadProfile]);

  let screen;
  if (user === undefined) screen = html`<${Loading} />`;
  else if (user === null) screen = html`<${AuthScreen} notify=${notify} />`;
  else if (profile === undefined) screen = html`<${Loading} />`;
  else if (profile === null) screen = html`<${CompleteProfile} email=${user.email} onDone=${loadProfile} notify=${notify} />`;
  else screen = html`<${Dashboard} profile=${profile} notify=${notify} />`;

  return html`<${React.Fragment}>${screen}<${Toasts} toasts=${toasts} /></${React.Fragment}>`;
}

createRoot(document.getElementById("root")).render(html`<${App} />`);
