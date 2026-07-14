# Kredi Analiz — Akış & Tasarım Dokümanı

> **Durum:** Taslak (design review için) · **Dal:** `feature/kredi-analiz`
> **Amaç:** Bir müşterinin başvurduğu kredi talebinin, mevcut hesap/işlem verilerine
> dayanarak otomatik değerlendirilmesi (skorlama + karar) için akışı tanımlamak.
> Bu doküman **implementasyon içermez**; implementasyon ayrı bir dalda
> (`feature/kredi-analiz-impl`) yapılacaktır.

---

## 1. Kapsam

### Yapılacaklar (in-scope)
- Müşteri bir **kredi başvurusu** (`CreditApplication`) oluşturur: talep edilen tutar, vade.
- Sistem, müşterinin mevcut verilerinden bir **kredi skoru** hesaplar.
- Skora ve kurallara göre otomatik bir **karar** verilir: `APPROVED` / `REJECTED` /
  `MANUAL_REVIEW`.
- Başvuru ve karar kalıcı olarak saklanır ve denetlenebilir (audit) olur.

### Yapılmayacaklar (out-of-scope, bu iterasyon)
- Gerçek kredi tahsisi / para aktarımı (onaylanınca hesaba yatırma) — ayrı bir iş.
- Harici kredi bürosu (KKB vb.) entegrasyonu — ileride bir `out` portu olarak eklenebilir.
- Makine öğrenmesi tabanlı skorlama — ilk sürüm **kural tabanlı**.

---

## 2. Domain Kavramları

Mevcut mimari **hexagonal (ports & adapters)**. Yeni kavramlar aynı katmanlara oturur:

| Kavram | Katman | Açıklama |
|--------|--------|----------|
| `CreditApplication` | `domain/model` | Başvuru: müşteri, tutar, vade, durum, karar, skor. |
| `CreditDecision` (enum) | `domain` | `APPROVED`, `REJECTED`, `MANUAL_REVIEW`. |
| `CreditApplicationStatus` (enum) | `domain` | `SUBMITTED`, `EVALUATED`. |
| `CreditScore` | `domain/model` (value) | 0–1000 arası skor + gerekçe kalemleri. |

### Skorlama girdileri (mevcut veriden türetilir)
- Müşterinin toplam bakiyesi (tüm `Account`'ların toplamı).
- Son N günlük işlem hacmi / düzenliliği (`LedgerEntry`).
- Hesap yaşı (`Customer.createdAt`).
- Müşteri durumu (`CustomerStatus.ACTIVE` değilse otomatik `REJECTED`).

---

## 3. Akış (Sequence)

```
Müşteri                API (Controller)         CreditService          Repository/Portlar
  |                          |                        |                        |
  |  POST /credit-applications                        |                        |
  |------------------------->|                        |                        |
  |                          |  submit(cmd)           |                        |
  |                          |----------------------->|                        |
  |                          |                        | müşteri/hesap/ledger oku|
  |                          |                        |----------------------->|
  |                          |                        |<-----------------------|
  |                          |                        | score = calculate(...) |
  |                          |                        | decision = decide(score)|
  |                          |                        | save(application)      |
  |                          |                        |----------------------->|
  |                          |  CreditApplicationView |                        |
  |                          |<-----------------------|                        |
  |  201 Created + karar     |                        |                        |
  |<-------------------------|                        |                        |
```

Değerlendirme **senkron** yapılır (ilk sürüm): başvuru anında skor+karar döner.
İleride ağır hesaplama gerekirse asenkron kuyruk (`SUBMITTED` → arka planda `EVALUATED`)
modeline geçilebilir; durum makinesi buna uygun tasarlandı.

---

## 4. Portlar (Hexagonal)

### Giriş portları (`application/port/in`)
```java
public interface SubmitCreditApplicationUseCase {
    CreditApplicationResult submit(SubmitCreditApplicationCommand command);
}
public interface GetCreditApplicationUseCase {
    CreditApplicationResult getById(Long applicationId, Long requestingCustomerId);
}
```

### Çıkış portları (`application/port/out`)
```java
public interface CreditApplicationRepository {
    CreditApplication save(CreditApplication application);
    Optional<CreditApplication> findById(Long id);
    List<CreditApplication> findByCustomerId(Long customerId);
}
// Skorlama, test edilebilir ve değiştirilebilir olsun diye ayrı bir port:
public interface CreditScoringPolicy {
    CreditScore score(CreditScoringInputs inputs);
}
```

Bu ayrım sayesinde skorlama kuralları (`CreditScoringPolicy`) ileride harici bir servise
(adapter değişikliği) taşınabilir; `application/service` katmanı değişmez.

---

## 5. Skorlama & Karar Kuralları (ilk sürüm, kural tabanlı)

Skor 0–1000. Örnek ağırlıklar (design review'da netleşecek):

| Kriter | Katkı |
|--------|-------|
| Toplam bakiye ≥ talep tutarının 3 katı | +300 |
| Hesap yaşı ≥ 6 ay | +200 |
| Son 90 günde düzenli para girişi | +250 |
| Negatif/şüpheli işlem yok | +150 |
| Taban puan | +100 |

**Karar eşiği:**
- `score ≥ 700` → **APPROVED**
- `400 ≤ score < 700` → **MANUAL_REVIEW**
- `score < 400` veya müşteri `ACTIVE` değil → **REJECTED**

> Not: Eşikler ve ağırlıklar `application.yml`'de konfigüre edilebilir olacak
> (`banking.credit.*`), tıpkı mevcut `banking.security.*` gibi.

---

## 6. Veri Modeli (Flyway migration taslağı)

Yeni tablo — sıradaki migration `V9__create_credit_application.sql`:

```sql
CREATE TABLE credit_application (
    id              BIGSERIAL PRIMARY KEY,
    customer_id     BIGINT NOT NULL REFERENCES customer(id),
    amount          NUMERIC(19,2) NOT NULL,
    term_months     INTEGER NOT NULL,
    score           INTEGER,
    decision        VARCHAR(20),       -- APPROVED | REJECTED | MANUAL_REVIEW
    status          VARCHAR(20) NOT NULL, -- SUBMITTED | EVALUATED
    created_at      TIMESTAMP NOT NULL,
    version         BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_credit_application_customer ON credit_application(customer_id);
```

---

## 7. API Uç Noktaları

| Method | Path | Yetki | Açıklama |
|--------|------|-------|----------|
| `POST` | `/credit-applications` | CUSTOMER | Yeni başvuru + anlık karar. |
| `GET`  | `/credit-applications/{id}` | Sahip müşteri veya ADMIN | Başvuru detayını getir. |
| `GET`  | `/credit-applications` | CUSTOMER | Müşterinin kendi başvuruları. |
| `GET`  | `/admin/credit-applications?decision=MANUAL_REVIEW` | ADMIN | Manuel inceleme kuyruğu. |

**İstek örneği:**
```json
POST /credit-applications
{ "amount": 50000.00, "termMonths": 12 }
```
**Yanıt örneği:**
```json
{
  "id": 42,
  "amount": 50000.00,
  "termMonths": 12,
  "score": 750,
  "decision": "APPROVED",
  "status": "EVALUATED",
  "reasons": ["Yeterli bakiye", "Hesap yaşı > 6 ay"]
}
```

---

## 8. Çapraz Kesen Konular

- **Güvenlik / RBAC:** Mevcut `SecurityConfig` + `AccountAccessGuard` deseni izlenir;
  müşteri yalnızca kendi başvurusunu görür, `/admin/**` yalnızca `ADMIN`.
- **Idempotency:** `POST /credit-applications` mevcut `IdempotencyRecord` mekanizmasıyla
  `Idempotency-Key` başlığını destekler (çift başvuru önleme).
- **Audit:** Her karar mevcut `OperationLogEntry` altyapısıyla loglanır
  (`OperationType.CREDIT_DECISION` eklenir).
- **Concurrency:** `@Version` optimistic lock deseni `CreditApplication`'da da kullanılır.
- **Validation:** Tutar > 0, vade 1–60 ay; mevcut `ValidationException` ile.

---

## 9. Test Stratejisi (implementasyon dalında)

- **Birim:** `CreditScoringPolicy` kural testleri (sınır değerler: 399/400/699/700).
- **Entegrasyon (Testcontainers):** başvuru → karar → DB'de kayıt akışı; RBAC (başkasının
  başvurusunu görememe); idempotency (aynı key ile tek kayıt).
- Mevcut `AbstractIntegrationTest` altyapısı yeniden kullanılır.

---

## 10. Uygulama Adımları (implementasyon dalı için checklist)

1. `domain`: `CreditApplication`, `CreditDecision`, `CreditApplicationStatus`, `CreditScore`.
2. `port/out`: `CreditApplicationRepository`, `CreditScoringPolicy`.
3. `port/in`: `SubmitCreditApplicationUseCase`, `GetCreditApplicationUseCase`.
4. `application/service`: `CreditService` + `RuleBasedCreditScoringPolicy`.
5. `adapter/out/persistence`: JPA entity + repository adapter.
6. `adapter/in/web`: `CreditController` + DTO'lar.
7. `resources/db/migration`: `V9__create_credit_application.sql`.
8. `config`: `banking.credit.*` özellikleri.
9. Testler (bkz. §9).

---

**Sonraki adım:** Bu taslak `feature/kredi-analiz` dalından bir Pull Request ile ekibe
sunulur. Onay/geri bildirim sonrası implementasyon `feature/kredi-analiz-impl` dalında
bu checklist izlenerek yapılır.
