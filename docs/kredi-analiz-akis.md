# Kredi Analiz — Akış & Tasarım Dokümanı

> **Durum:** Taslak (design review için) · **Dal:** `feature/kredi-analiz`
> **Amaç:** Bir müşterinin kredi başvurusunun; **atanmış bir bankacı** tarafından
> gerçek bir underwriting (kredi tahsis) sürecine benzer şekilde değerlendirilmesi,
> onaylanırsa paranın müşterinin hesabına aktarılması ve müşteriye bir **geri ödeme
> planı** (taksit tablosu) sunulması.
>
> Bu doküman **implementasyon içermez**; implementasyon `feature/kredi-analiz-impl`
> dalında yapılır. Akış, gerçek TR banka kredi süreçleri araştırılarak tasarlanmıştır
> (bkz. §11 Kaynaklar).

---

## 1. Roller

| Rol | Yetki |
|-----|-------|
| **CUSTOMER** | Kredi başvurusu yapar, kendi başvurularını ve geri ödeme planını görür. |
| **BANKER** (yeni) | Yalnızca **kendisine atanmış** müşterilerin başvurularını görür ve değerlendirir (onay/ret). |
| **ADMIN** | Tüm başvuruları görür; bankacı–müşteri atamalarını yönetir/denetler. |

> `CustomerRole` enum'ına `BANKER` eklenir. Yetkilendirme mevcut Spring Security
> `hasRole(...)` deseniyle yapılır; bankacının "sadece kendi müşterisi" kısıtı ayrıca
> servis katmanında (`BankerAccessGuard`) zorlanır — tıpkı `AccountAccessGuard` gibi.

### Bankacı–Müşteri Ataması (random)
- Her **yeni müşteri kaydında** (`CustomerService.register`), sistemdeki BANKER rolüne
  sahip kullanıcılardan **rastgele biri** müşteriye atanır (`customer_banker_assignment`).
- Atama yoksa (hiç bankacı yoksa) kayıt yine de başarılı olur; başvuru anında tekrar
  atama denenir. Bankacı ekipleri arasında yük dağılımı için random seçim kullanılır.

---

## 2. Başvuru Alanları

Müşteri başvuruda şunları verir/beyan eder:

| Alan | Açıklama |
|------|----------|
| `productCode` | Seçilen **kredi ürünü** (ör. `IHTIYAC`, `TASIT`) — faiz oranı ve limit taşır. |
| `amount` | Talep edilen kredi tutarı. |
| `termMonths` | Vade (ay). |
| `monthlyIncome` | Beyan edilen aylık net gelir. |
| `profession` | Meslek. |
| `employmentMonths` | Aynı işte kaç aydır çalıştığı. |
| `disbursementAccountId` | Onaylanırsa paranın yatırılacağı kendi hesabı. |

---

## 3. Uçtan Uca Akış

```
1) CUSTOMER  ── POST /api/credit-applications ─────────────►  başvuru (status=SUBMITTED)
                 (gelir, meslek, çalışma süresi, ürün, tutar, vade)
                 └─ sistem: atanmış bankacıya yönlendirir + ön-metrikleri hesaplar
                    (aylık taksit, taksit/gelir oranı) — KARAR VERMEZ

2) BANKER    ── GET  /api/banker/credit-applications ──────►  kendi kuyruğu (SUBMITTED)
             ── GET  /api/banker/credit-applications/{id} ─►  detay + underwriting kriterleri
             ── POST .../{id}/approve  |  .../{id}/reject ─►  KARAR

3) Onay      ── sistem: MoneyMovementService ile parayı müşterinin hesabına aktarır
                 (OperationType.CREDIT_DISBURSEMENT) → status=APPROVED, disbursedAt set
                 └─ geri ödeme planı üretilir (anüite)

4) CUSTOMER  ── GET /api/accounts/{id}/statement ──────────►  krediyi transaction geçmişinde görür
             ── GET /api/credit-applications/{id}/repayment-plan ► taksit tablosu (mevzuat)
```

Değerlendirme **manuel** (bankacı kararı). Sistem otomatik onay/ret vermez; yalnızca
bankacıya **karar destek metrikleri** sunar.

---

## 4. Underwriting Kriterleri (bankacı ekranında görünür)

Araştırmadan çıkan gerçek TR banka kuralları temel alınır:

| Kriter | Kural / Gösterim | Kaynak |
|--------|------------------|--------|
| **Taksit/Gelir oranı** | Aylık taksit ≤ aylık gelirin **%50**'si (aşarsa kırmızı bayrak) | Tüm TR bankaları |
| **Çalışma süresi** | Aynı işte ≥ **3 ay** (özel sektör tipik alt sınır) | Genel şart |
| **Ürün limiti** | `amount` ürünün min/max limitleri içinde | Ürün tanımı |
| **Aylık taksit** | Anüite formülüyle hesaplanır (bkz. §6) | — |
| **Toplam maliyet** | Taksit × vade; toplam faiz = toplam − anapara | — |
| **Müşteri durumu** | `ACTIVE` değilse başvuru reddedilmeli | — |

Bu metrikler **öneri**dir; nihai karar bankacınındır. Kriterler `banking.credit.*`
altında konfigüre edilebilir (taksit/gelir eşiği, min çalışma ayı vb.).

---

## 5. Durum Makinesi

```
        submit()                approve()  ──► disbursement + repayment plan
SUBMITTED ───────► (bankacı) ──┤
                                └► reject()  ──► (para hareketi yok)

Durumlar: SUBMITTED → APPROVED | REJECTED   (APPROVED/REJECTED terminal)
```

---

## 6. Taksit & Geri Ödeme Planı (Anüite)

Eşit taksitli (anüite) yöntem — tüm TR bankalarının ihtiyaç kredisi standardı:

```
Aylık taksit:  T = A · r·(1+r)^n / ((1+r)^n − 1)
  A = anapara (kredi tutarı), r = aylık faiz oranı, n = vade (ay)
r = 0 ise:      T = A / n
```

**Amortisman tablosu** her taksit için: faiz payı = kalan anapara · r; anapara payı =
T − faiz payı; kalan anapara güncellenir. İlk aylarda faiz payı yüksek, sonlara doğru
anapara payı artar. Son taksitte yuvarlama farkı düzeltilir (kalan = 0).

Hesap `BigDecimal` ile yapılır (mevcut `Money` deseni, HALF_UP, 2 ondalık).

---

## 7. Veri Modeli (Flyway migration'ları)

Sıradaki numaralar (mevcut son migration V8):

- **V9 — `credit_product`**: `code (PK-ish, unique)`, `name`, `annual_interest_rate`,
  `min_amount`, `max_amount`, `max_term_months`. Seed: IHTIYAC, TASIT.
- **V10 — `customer.role` CHECK güncelle**: `('CUSTOMER','BANKER','ADMIN')`.
- **V11 — `customer_banker_assignment`**: `customer_id (unique)`, `banker_id`, `assigned_at`.
- **V12 — `credit_application`**: aşağıdaki alanlar.
- **V13 — seed banker kullanıcılar** (2 bankacı) — demo/dev için.

```sql
CREATE TABLE credit_application (
    id                       BIGSERIAL PRIMARY KEY,
    customer_id              BIGINT NOT NULL REFERENCES customer(id),
    banker_id                BIGINT REFERENCES customer(id),   -- değerlendiren bankacı
    product_code             VARCHAR(30) NOT NULL,
    amount                   NUMERIC(19,2) NOT NULL,
    term_months              INTEGER NOT NULL,
    annual_interest_rate     NUMERIC(9,6) NOT NULL,            -- başvuru anında ürün oranı
    monthly_income           NUMERIC(19,2) NOT NULL,
    profession               VARCHAR(120) NOT NULL,
    employment_months        INTEGER NOT NULL,
    disbursement_account_id  BIGINT REFERENCES account(id),
    monthly_installment      NUMERIC(19,2),                    -- hesaplanan taksit
    status                   VARCHAR(20) NOT NULL,             -- SUBMITTED|APPROVED|REJECTED
    decision_reason          VARCHAR(500),
    created_at               TIMESTAMP NOT NULL,
    decided_at               TIMESTAMP,
    disbursed_at             TIMESTAMP,
    version                  BIGINT NOT NULL DEFAULT 0
);
```

Geri ödeme planı **türetilir** (kalıcı saklanmaz; başvurunun tutar/oran/vade'sinden
her istekte hesaplanır). İleride sözleşme dondurma gerekirse ayrı tabloya alınabilir.

---

## 8. API Uç Noktaları (mevcut `/api` prefix'i ile)

| Method | Path | Rol | Açıklama |
|--------|------|-----|----------|
| `GET`  | `/api/credit-products` | CUSTOMER | Seçilebilir kredi ürünleri. |
| `POST` | `/api/credit-applications` | CUSTOMER | Başvuru (Idempotency-Key destekli). |
| `GET`  | `/api/credit-applications` | CUSTOMER | Müşterinin kendi başvuruları. |
| `GET`  | `/api/credit-applications/{id}` | Sahip müşteri | Başvuru detayı. |
| `GET`  | `/api/credit-applications/{id}/repayment-plan` | Sahip müşteri | Taksit tablosu. |
| `GET`  | `/api/banker/credit-applications` | BANKER | Kendi müşterilerinin SUBMITTED kuyruğu. |
| `GET`  | `/api/banker/credit-applications/{id}` | BANKER | Detay + underwriting kriterleri. |
| `POST` | `/api/banker/credit-applications/{id}/approve` | BANKER | Onay → disbursement. |
| `POST` | `/api/banker/credit-applications/{id}/reject` | BANKER | Ret (+ gerekçe). |
| `GET`  | `/api/admin/credit-applications` | ADMIN | Tüm başvurular. |

---

## 9. Çapraz Kesen Konular

- **Güvenlik/RBAC:** `BANKER` rolü `SecurityConfig`'e eklenir (`/api/banker/**` →
  `hasRole("BANKER")`). Bankacı yalnızca kendine atanmış müşterinin başvurusuna erişir
  (`BankerAccessGuard`, ihlalde 404 — enumerasyonu önler, `AccountAccessGuard` gibi).
- **Disbursement:** Onayda para hareketi mevcut `MoneyMovementService` double-entry
  altyapısıyla, EXTERNAL_CASH karşı-bacağıyla yapılır → yeni `OperationType.CREDIT_DISBURSEMENT`.
  Böylece kredi, müşterinin **transaction geçmişinde** görünür.
- **Idempotency:** `POST /api/credit-applications` ve approve, mevcut `IdempotencyRecord`
  mekanizmasıyla çift işlem/çift ödeme önler.
- **Audit:** Her karar + disbursement mevcut `OperationLogEntry` ile loglanır.
- **Concurrency:** `CreditApplication` `@Version` optimistic lock; iki bankacının aynı
  başvuruyu aynı anda karara bağlaması engellenir.
- **Validation:** tutar > 0 ve ürün limitinde; vade 1..product.maxTerm; gelir > 0;
  employmentMonths ≥ 0. Mevcut `ValidationException`/`GlobalExceptionHandler`.

---

## 10. İmplementasyon Checklist (impl dalı)

1. `domain`: `CustomerRole.BANKER`; `CreditApplication`, `CreditApplicationStatus`,
   `CreditProduct`, `CustomerBankerAssignment`; `RepaymentPlan`/`Installment` (value) +
   `Annuity` hesaplayıcı; `OperationType.CREDIT_DISBURSEMENT`.
2. `port/out`: `CreditApplicationRepository`, `CreditProductRepository`,
   `BankerAssignmentRepository`.
3. `port/in`: `SubmitCreditApplicationUseCase`, `GetCreditApplicationUseCase`,
   `ListCreditApplicationsUseCase`, `EvaluateCreditApplicationUseCase`,
   `GetRepaymentPlanUseCase`, `ListCreditProductsUseCase`.
4. `application/service`: `CreditApplicationService`, `CreditEvaluationService`
   (disbursement dahil), `BankerAssignmentService`, `RepaymentPlanCalculator`,
   `BankerAccessGuard`.
5. `adapter/out/persistence`: JPA entity + repository adapter'lar.
6. `adapter/in/web`: `CreditController`, `BankerCreditController` + DTO'lar.
7. `resources/db/migration`: V9–V13.
8. `infrastructure/security`: `SecurityConfig` BANKER yetkileri.
9. `config`: `banking.credit.*`.
10. Testler (§9 kuralları + uçtan uca akış, RBAC, idempotency).

---

## 11. Kaynaklar (araştırma)

- [Kredi Başvuru Süreci — Hesapkurdu](https://www.hesapkurdu.com/ihtiyac-kredisi/rehber/kredi-basvuru-sureci)
- [Kredi Başvurusu Nasıl Değerlendirilir? — Kobitek](https://kobitek.com/kredi-basvurusu-nasil-degerlendirilir)
- [Kredi Çekme Şartları — Hesapkurdu](https://www.hesapkurdu.com/ihtiyac-kredisi/rehber/kredi-cekme-sartlari)
- [Kredi Taksit Hesaplama (anüite) — HesapMod](https://www.hesapmod.com/finansal-hesaplamalar/kredi-taksit-hesaplama)
- [Kredi Hesaplama Formülü/Matematiği — KrediModeli](https://www.kredimodeli.com/makaleler/KrediHesaplama)
- [Loan Origination Process — FICO](https://www.fico.com/en/glossary/loan-originations-and-onboarding)
- [10 Stages in the Loan Origination Process — CloudBankin](https://cloudbankin.com/blog/loan-origination/10-stages-in-the-loan-origination-process/)

---

**Not (yorum):** Başvuruda "kart seçimi", para hesaba aktarılıp taksitle geri ödendiği
için **kredi ürünü/türü seçimi** olarak modellenmiştir. Farklı bir anlam (ör. gerçek
kredi kartı ürünü) kastedildiyse review'da güncellenecektir.
