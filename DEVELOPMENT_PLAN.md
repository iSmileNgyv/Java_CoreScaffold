# EFinance — Money Manager ERP Development Plan

## Mündəricat
1. [Layihə Baxışı](#layihə-baxışı)
2. [Arxitektura](#arxitektura)
3. [Protokollar](#protokollar)
4. [Microservices](#microservices)
5. [Database Schema](#database-schema)
6. [İnfrastruktur](#infrastruktur)
7. [Development Fazaları](#development-fazaları)
8. [API Konvensiyaları](#api-konvensiyaları)
9. [Multi-Tenancy](#multi-tenancy)

---

## Layihə Baxışı

**EFinance** — ERP-stil multi-tenant pul idarəetmə sistemi.

### Əsas Funksionallıq
- Gəlir / Xərc izlənməsi
- Məhsul kataloqu (barcode, kateqoriya, stok, qiymət)
- Multi-valyuta + tarixə görə məzənnə
- Sonsuz alt-kateqoriya sistemi (gəlir/xərc + məhsul)
- ERP-stil ledger sistemi (`e_item_ledger_entry`, `e_value_entry`)
- Hər cür report + real-time dashboard
- Multi-tenant (hər tenant öz schema-sında)

---

## Arxitektura

```
┌──────────────────────────────────────────────────────────────────┐
│                    Client (Web / Mobile / Desktop)               │
└───────────────────────────┬──────────────────────────────────────┘
                            │ REST / JSON
┌───────────────────────────▼──────────────────────────────────────┐
│                      Envoy API Gateway                           │
│                         Port: 9090                               │
└──┬──────────┬────────────┬────────────┬───────────┬─────────────┘
   │          │            │            │           │
   ▼          ▼            ▼            ▼           ▼
[Auth]    [Catalog]    [Finance]    [Storage]   [Report]
  │           │            │            │           │
  │        gRPC ──────────►│            │        SSE/REST
  │           │         gRPC ──────────►│           │
  │           └─────────────────────────┘           │
  └──────── gRPC (token validation) ────────────────┘
                            │
                          Kafka
                            │
                     ┌──────┴──────┐
                     │   Debezium  │
                     │    (CDC)    │
                     └──────┬──────┘
                            │
                       ClickHouse
```

---

## Protokollar

| Qat | Stil | Format | Transport | Səbəb |
|-----|------|--------|-----------|-------|
| Client → Gateway | REST | JSON | HTTP/1.1 & HTTP/2 | Universal, sadə |
| Gateway → Service | REST | JSON | HTTP/1.1 & HTTP/2 | Eyni |
| Service → Service | gRPC | Protobuf | HTTP/2 (məcburi) | Sürət, type-safety, .proto contract |
| Async events | Kafka | JSON / Avro | TCP | Decoupling, CDC |
| Real-time dashboard | SSE | JSON | HTTP | Sadə unidirectional axın |
| File transfer | REST multipart | Binary | HTTP | Storage service |

---

## Microservices

### Mövcud (Scaffold-dan gəlir)

| Servis | Port | Texnologiya | İstifadə |
|--------|------|-------------|---------|
| `Auth` | 8081 / gRPC 5051 | Spring Boot 3.2.7 + gRPC + JWT | İstifadəçi auth, tenant idarəsi, JWT |
| `OTP` | 8082 / gRPC 5052 | Spring Boot + Redis + Kafka | 2FA dəstəyi |
| `Notification` | 8083 / gRPC 5053 | Spring Boot + WebSocket + Email | Bildirişlər |

### Yeni Servisler

| Servis | Port | gRPC | DB | Məqsəd |
|--------|------|------|----|--------|
| `Catalog` | 8084 | 5054 | PostgreSQL | Məhsul, kateqoriya, barcode |
| `Finance` | 8085 | 5055 | PostgreSQL | Ledger, tranzaksiya, valyuta |
| `Storage` | 8086 | 5056 | MinIO + PostgreSQL | Fayl, şəkil, qəbz |
| `Report` | 8087 | — | ClickHouse | Analitika, report, dashboard |

---

## Database Schema

### e_currency
```sql
CREATE TABLE e_currency (
    id              BIGSERIAL PRIMARY KEY,
    code            VARCHAR(3) NOT NULL UNIQUE,    -- AZN, USD, EUR
    name            VARCHAR(100) NOT NULL,          -- Azərbaycan Manatı
    symbol          VARCHAR(5) NOT NULL,            -- ₼, $, €
    decimal_places  INT NOT NULL DEFAULT 2,
    is_base         BOOLEAN NOT NULL DEFAULT FALSE, -- yalnız 1 base ola bilər
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);
```

### e_currency_exchange_rate
```sql
CREATE TABLE e_currency_exchange_rate (
    id               BIGSERIAL PRIMARY KEY,
    currency_id      BIGINT NOT NULL REFERENCES e_currency(id),
    base_currency_id BIGINT NOT NULL REFERENCES e_currency(id),
    rate             DECIMAL(18,6) NOT NULL,   -- 1 USD = 1.702500 AZN
    valid_from       DATE NOT NULL,
    created_at       TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (currency_id, base_currency_id, valid_from)
);
-- Sorğu: valid_from <= posting_date ORDER BY valid_from DESC LIMIT 1
```

### e_item_category
```sql
CREATE TABLE e_item_category (
    id          BIGSERIAL PRIMARY KEY,
    parent_id   BIGINT REFERENCES e_item_category(id),  -- NULL = kök
    code        VARCHAR(50) NOT NULL UNIQUE,
    name        VARCHAR(200) NOT NULL,
    path        VARCHAR(1000),   -- "Elektronika/Telefon/Android" (denormalized)
    depth       INT NOT NULL DEFAULT 0,
    active      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);
```

### e_item
```sql
CREATE TABLE e_item (
    id          BIGSERIAL PRIMARY KEY,
    category_id BIGINT NOT NULL REFERENCES e_item_category(id),
    name        VARCHAR(300) NOT NULL,
    barcode     VARCHAR(100),
    description TEXT,
    base_unit   VARCHAR(20) NOT NULL DEFAULT 'unit',  -- unit, kg, litre
    image_url   VARCHAR(500),   -- Storage servisindən gəlir
    active      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP
);
```

### e_finance_category
```sql
CREATE TABLE e_finance_category (
    id          BIGSERIAL PRIMARY KEY,
    parent_id   BIGINT REFERENCES e_finance_category(id),  -- NULL = kök
    name        VARCHAR(200) NOT NULL,
    type        VARCHAR(20) NOT NULL,   -- INCOME, EXPENSE
    path        VARCHAR(1000),
    depth       INT NOT NULL DEFAULT 0,
    active      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);
```

### e_item_ledger_entry  ← FİZİKİ HƏRƏKƏT
```sql
CREATE TABLE e_item_ledger_entry (
    id            BIGSERIAL PRIMARY KEY,
    entry_no      BIGINT NOT NULL UNIQUE,    -- global sıra nömrəsi
    item_id       BIGINT NOT NULL REFERENCES e_item(id),
    entry_type    VARCHAR(30) NOT NULL,
    --  PURCHASE        : alış (stok artır)
    --  SALE            : satış (stok azalır)
    --  POS_ADJUSTMENT  : müsbət düzəliş
    --  NEG_ADJUSTMENT  : mənfi düzəliş
    --  TRANSFER        : köçürmə
    quantity      DECIMAL(18,6) NOT NULL,    -- + giriş / - çıxış
    remaining_qty DECIMAL(18,6) NOT NULL,    -- bağlanmamış miqdar (FIFO)
    posting_date  DATE NOT NULL,
    document_no   VARCHAR(100),              -- faktura, qəbz nömrəsi
    description   TEXT,
    open          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP NOT NULL DEFAULT NOW()
);
```

### e_value_entry  ← PUL DƏYƏRİ
```sql
CREATE TABLE e_value_entry (
    id                      BIGSERIAL PRIMARY KEY,
    entry_no                BIGINT NOT NULL UNIQUE,
    item_ledger_entry_id    BIGINT REFERENCES e_item_ledger_entry(id),  -- NULL ola bilər
    finance_category_id     BIGINT REFERENCES e_finance_category(id),   -- NULL ola bilər
    entry_type              VARCHAR(30) NOT NULL,
    --  DIRECT_COST  : birbaşa xərc (məhsul alışı)
    --  INCOME       : gəlir
    --  EXPENSE      : xərc
    --  ADJUSTMENT   : düzəliş
    --  ROUNDING     : yuvarlama fərqi
    source_type             VARCHAR(20) NOT NULL,
    --  ITEM     : item ledger entry-dən gəlir
    --  FINANCE  : xalis gəlir/xərc (məhsutsuz)
    --  MANUAL   : əl ilə daxil edilib
    amount                  DECIMAL(18,4) NOT NULL,
    currency_id             BIGINT NOT NULL REFERENCES e_currency(id),
    currency_factor         DECIMAL(18,6) NOT NULL,  -- o anki məzənnə (snapshot!)
    amount_lcy              DECIMAL(18,4) NOT NULL,  -- amount × currency_factor (AZN)
    posting_date            DATE NOT NULL,
    document_no             VARCHAR(100),
    description             TEXT,
    created_at              TIMESTAMP NOT NULL DEFAULT NOW()
);
```

### Axış Nümunəsi
```
Məhsul alındı (10 ədəd telefon, 150 USD):
  e_item_ledger_entry → type=PURCHASE, qty=+10, item_id=42
  e_value_entry       → type=DIRECT_COST, source=ITEM,
                         amount=150 USD, factor=1.7025, amount_lcy=255.38 AZN
                         item_ledger_entry_id=↑

Telefon satıldı (3 ədəd, 60 USD):
  e_item_ledger_entry → type=SALE, qty=-3, item_id=42
  e_value_entry       → type=DIRECT_COST, source=ITEM,
                         amount=60 USD, factor=1.7102, amount_lcy=102.61 AZN

İcarə xərci (xalis, məhsutsuz, 500 AZN):
  e_value_entry       → type=EXPENSE, source=FINANCE,
                         item_ledger_entry_id=NULL,
                         finance_category_id=→"İcarə",
                         amount=500 AZN, factor=1.0, amount_lcy=500 AZN

İstənilən tarix üzrə report:
  SELECT SUM(amount_lcy) FROM e_value_entry
  WHERE posting_date BETWEEN '2025-01-01' AND '2025-03-31'
  GROUP BY entry_type;
  -- Bütün məzənnə fərqləri artıq amount_lcy-də işlənmiş
```

---

## İnfrastruktur

### Docker Stack (docker-compose.yml genişləndirilir)

| Komponent | Port | İstifadə |
|-----------|------|---------|
| Envoy Gateway | 9090 | API Gateway |
| PostgreSQL (auth) | 5433 | Auth service DB |
| PostgreSQL (otp) | 5434 | OTP service DB |
| PostgreSQL (notification) | 5435 | Notification service DB |
| PostgreSQL (catalog) | 5437 | Catalog service DB |
| PostgreSQL (finance) | 5438 | Finance service DB — schema-per-tenant |
| PostgreSQL (storage_meta) | 5439 | Storage metadata |
| ClickHouse | 8123 / 9000 | Report OLAP DB |
| MinIO | 9000 / 9001 | Object storage (şəkillər, fayllar) |
| Kafka + Zookeeper | 9092 / 2181 | Event streaming |
| Debezium | 8083 | CDC — PostgreSQL → Kafka |
| Redis | 6379 | Cache (OTP, session) |
| Kafka UI | 8080 | Kafka monitoring |
| ClickHouse UI | 8124 | ClickHouse monitoring |

---

## Multi-Tenancy

**Strategiya: Schema-per-Tenant (PostgreSQL)**

```
PostgreSQL (finance DB)
├── schema: tenant_abc
│   ├── e_currency
│   ├── e_currency_exchange_rate
│   ├── e_item_category
│   ├── e_item
│   ├── e_finance_category
│   ├── e_item_ledger_entry
│   └── e_value_entry
│
├── schema: tenant_xyz
│   ├── e_currency
│   └── ...
│
└── schema: public (sistem cədvəlləri)
    └── e_tenant (tenant registry)
```

**Tenant Routing:**
- JWT token içində `tenant_id` claim
- Hər requestdə Spring `CurrentTenantIdentifierResolver` schema seçir
- Hibernate multi-tenancy: `SCHEMA` strategiyası

---

## Development Fazaları

---

### Faza 0 — Altyapı Hazırlığı
> Hər şeydən əvvəl, infrastruktur qurulur

**Tasks:**
- [ ] `docker-compose.yml` yenilənir — yeni PostgreSQL, ClickHouse, MinIO, Debezium əlavə edilir
- [ ] `settings.gradle` yenilənir — Catalog, Finance, Storage, Report modülləri əlavə edilir
- [ ] `build.gradle` root — ortaq dependency versiyaları (BOM)
- [ ] Envoy `envoy.yaml` yenilənir — yeni servis route-ları
- [ ] Hər yeni servis üçün `build.gradle` skeleton
- [ ] Hər yeni servis üçün `application.yml` skeleton
- [ ] Multi-tenant `TenantContext`, `TenantIdentifierResolver`, `TenantConnectionProvider`
- [ ] Base entity sinifləri (`BaseEntity`, `AuditableEntity`)
- [ ] Global exception handler (hər servisə ortaq)
- [ ] Kafka topic-lərinin yaradılması

**Çıxış:** Docker `up` ilə bütün infrastruktur ayağa qalxır

---

### Faza 1 — Auth Genişləndirilməsi
> Mövcud Auth servisə tenant idarəsi əlavə edilir

**Tasks:**
- [ ] `e_tenant` cədvəli əlavə edilir (id, name, schema_name, plan, active)
- [ ] Tenant CRUD endpoint-ləri (superadmin)
- [ ] Tenant qeydiyyatı zamanı PostgreSQL schema avtomatik yaradılır
- [ ] JWT token-ə `tenant_id`, `tenant_schema` claim-ləri əlavə edilir
- [ ] Auth gRPC proto yenilənir — `tenant_id` sahəsi
- [ ] Tenant aktivasiya / deaktivasiya

**Çıxış:** Hər yeni tenant üçün öz schema-sı avtomatik yaranır

---

### Faza 2 — Catalog Service
> Məhsul və kateqoriya idarəsi

**Proto (catalog.proto):**
```protobuf
service CatalogService {
    rpc GetItem (GetItemRequest) returns (ItemResponse);
    rpc GetItemCategory (GetCategoryRequest) returns (CategoryResponse);
    rpc SearchItems (SearchItemsRequest) returns (SearchItemsResponse);
}
```

**REST Endpoints:**

| Method | Path | Açıqlama |
|--------|------|---------|
| POST | `/api/v1/categories` | Kateqoriya yarat |
| GET | `/api/v1/categories` | Kök kateqoriyalar |
| GET | `/api/v1/categories/{id}/children` | Alt kateqoriyalar |
| GET | `/api/v1/categories/{id}/tree` | Tam ağac |
| PUT | `/api/v1/categories/{id}` | Yenilə |
| DELETE | `/api/v1/categories/{id}` | Sil (boşdursa) |
| POST | `/api/v1/items` | Məhsul yarat |
| GET | `/api/v1/items` | Siyahı (filter: category, barcode) |
| GET | `/api/v1/items/{id}` | Tək məhsul |
| PUT | `/api/v1/items/{id}` | Yenilə |
| DELETE | `/api/v1/items/{id}` | Sil |
| GET | `/api/v1/items/barcode/{barcode}` | Barcode ilə axtar |

**Tasks:**
- [ ] Entity-lər: `ItemCategoryEntity`, `ItemEntity`
- [ ] Repository-lər + custom tree query-lər
- [ ] `CategoryService` — ağac əməliyyatları, path hesablanması
- [ ] `ItemService` — CRUD, barcode validasiya
- [ ] gRPC server implementasiyası
- [ ] REST controller-lər
- [ ] Multi-tenant schema routing
- [ ] Catalog → Auth gRPC (token validation interceptor)
- [ ] Unit testlər (kateqoriya ağacı, path hesablanması)
- [ ] Integration testlər (REST endpoint-lər)

**Çıxış:** Məhsul və kateqoriya tam işləyir

---

### Faza 3 — Storage Service
> Fayl və şəkil idarəsi

**REST Endpoints:**

| Method | Path | Açıqlama |
|--------|------|---------|
| POST | `/api/v1/storage/upload` | Fayl yüklə |
| GET | `/api/v1/storage/{fileId}` | Fayl al |
| DELETE | `/api/v1/storage/{fileId}` | Fayl sil |
| GET | `/api/v1/storage/{fileId}/url` | Signed URL al |

**Tasks:**
- [ ] MinIO Java SDK inteqrasiyası
- [ ] `StorageFileEntity` (metadata: fayl adı, növ, ölçü, bucket, key, tenant)
- [ ] `StorageService` — upload, download, delete, signed URL
- [ ] Fayl növü validasiyası (yalnız image/pdf/etc)
- [ ] Fayl ölçü limiti
- [ ] gRPC server — Catalog servis şəkil URL soruşur
- [ ] REST controller
- [ ] Bucket-per-tenant strategiyası (MinIO)

**Çıxış:** Məhsul şəkilləri yüklənib saxlanılır

---

### Faza 4 — Finance Service (Əsas)
> Bütün maliyyə əməliyyatları

**Proto (finance.proto):**
```protobuf
service FinanceService {
    rpc GetValueEntry (GetValueEntryRequest) returns (ValueEntryResponse);
    rpc GetItemLedgerEntry (GetItemLedgerRequest) returns (ItemLedgerResponse);
    rpc GetCurrentStock (GetStockRequest) returns (StockResponse);
}
```

**REST Endpoints:**

| Method | Path | Açıqlama |
|--------|------|---------|
| POST | `/api/v1/currencies` | Valyuta əlavə et |
| GET | `/api/v1/currencies` | Valyuta siyahısı |
| POST | `/api/v1/currencies/{id}/rates` | Məzənnə əlavə et |
| GET | `/api/v1/currencies/{id}/rates` | Məzənnə tarixi |
| GET | `/api/v1/currencies/{id}/rate?date=` | Tarixə görə məzənnə |
| POST | `/api/v1/finance-categories` | Gəlir/xərc kateqoriya |
| GET | `/api/v1/finance-categories` | Kateqoriya ağacı |
| POST | `/api/v1/transactions/purchase` | Alış əməliyyatı |
| POST | `/api/v1/transactions/sale` | Satış əməliyyatı |
| POST | `/api/v1/transactions/income` | Xalis gəlir |
| POST | `/api/v1/transactions/expense` | Xalis xərc |
| POST | `/api/v1/transactions/adjustment` | Stok düzəlişi |
| GET | `/api/v1/ledger/items/{itemId}` | Məhsul ledger tarixçəsi |
| GET | `/api/v1/ledger/items/{itemId}/stock` | Cari stok |
| GET | `/api/v1/value-entries` | Dəyər girişləri (filter+paginate) |

**Tasks:**
- [ ] Entity-lər: `CurrencyEntity`, `ExchangeRateEntity`, `FinanceCategoryEntity`, `ItemLedgerEntryEntity`, `ValueEntryEntity`
- [ ] `EntryNoGenerator` — global sıra nömrəsi generatoru (atomic, thread-safe)
- [ ] `ExchangeRateService` — tarixə görə məzənnə tapma
- [ ] `CurrencyConversionService` — məbləğ çevirmə + snapshot yazma
- [ ] `FinanceCategoryService` — ağac əməliyyatları
- [ ] `ItemLedgerService` — fiziki hərəkət qeydləri
- [ ] `ValueEntryService` — pul dəyəri qeydləri
- [ ] `TransactionOrchestrator` — alış/satış/gəlir/xərc atomik yazma
- [ ] Finance → Catalog gRPC (item məlumatı)
- [ ] Finance → Auth gRPC (token validation)
- [ ] Kafka producer — `finance.value_entry.created` topic
- [ ] Multi-tenant schema routing
- [ ] REST controller-lər
- [ ] Unit testlər (məzənnə tapma, çevirmə, stok hesabı)
- [ ] Integration testlər

**Çıxış:** Bütün maliyyə əməliyyatları işləyir, Kafka-ya event göndərilir

---

### Faza 5 — Report Service
> Analitika və dashboard

**ClickHouse Cədvəlləri:**
```sql
-- Finance-dən sync olunan dəyər girişləri
CREATE TABLE value_entry_mv (
    entry_no        UInt64,
    tenant_id       String,
    entry_type      String,
    source_type     String,
    amount          Decimal(18,4),
    currency_code   String,
    amount_lcy      Decimal(18,4),
    posting_date    Date,
    item_id         Nullable(UInt64),
    category_path   Nullable(String),
    created_at      DateTime
) ENGINE = MergeTree()
PARTITION BY toYYYYMM(posting_date)
ORDER BY (tenant_id, posting_date, entry_type);
```

**REST Endpoints:**

| Method | Path | Açıqlama |
|--------|------|---------|
| GET | `/api/v1/reports/summary` | Ümumi xülasə (query: from, to) |
| GET | `/api/v1/reports/monthly` | Aylıq gəlir/xərc |
| GET | `/api/v1/reports/by-category` | Kateqoriya üzrə |
| GET | `/api/v1/reports/items/{id}` | Məhsul profitability |
| GET | `/api/v1/reports/currency-exposure` | Valyuta pozisiyası |
| GET | `/api/v1/reports/cash-flow` | Cash flow (from, to) |
| GET | `/api/v1/dashboard/stream` | SSE — real-time dashboard data |
| POST | `/api/v1/reports/export` | PDF / Excel export (async) |

**Tasks:**
- [ ] ClickHouse JDBC inteqrasiyası
- [ ] Kafka consumer — `finance.value_entry.created` dinləyir
- [ ] ClickHouse-a yazma servisi
- [ ] `SummaryReportService` — cəm, ortalama, trend
- [ ] `MonthlyReportService` — aylıq breakdown
- [ ] `CategoryReportService` — kateqoriya ağacı ilə birləşdirilmiş
- [ ] `CashFlowService` — giriş/çıxış axını
- [ ] SSE endpoint — real-time dashboard stream
- [ ] Async export (Kafka task → fayl → Storage servisə yüklə)
- [ ] Report → Auth gRPC (token validation)
- [ ] Unit testlər (aggregation sorğuları)

**Çıxış:** Bütün reportlar işləyir, real-time dashboard aktiv

---

### Faza 6 — Test & Hardening

**Tasks:**
- [ ] Hər servis üçün tam integration test suite
- [ ] End-to-end test (ArgusOmni-CLI ilə YAML test suitlər)
- [ ] Performance testi — ClickHouse sorğu sürəti
- [ ] Security audit — JWT, tenant isolation, SQL injection
- [ ] Rate limiting yoxlanması (Bucket4j)
- [ ] Swagger/OpenAPI dokumentasiyası (hər REST servisə)
- [ ] gRPC reflection aktiv (development üçün)
- [ ] Debezium CDC sınanması (PostgreSQL → Kafka → ClickHouse)
- [ ] MinIO backup strategiyası
- [ ] ClickHouse backup strategiyası

**Çıxış:** Produksiyaya hazır sistem

---

### Faza 7 — Dockerization & DevOps

**Tasks:**
- [ ] Hər yeni servis üçün `Dockerfile` (multi-stage build)
- [ ] `docker-compose.yml` tam yenilənir
- [ ] Environment variable management (`.env` fayllar)
- [ ] Health check endpoint-lər (`/actuator/health`)
- [ ] Liveness / Readiness probe-lar
- [ ] Kafka UI, ClickHouse UI konfiqurasiya
- [ ] MinIO konsol konfiqurasiya
- [ ] Envoy `envoy.yaml` bütün yeni servis route-ları
- [ ] Log aggregation (strukturlu JSON logging)

**Çıxış:** `docker-compose up` ilə tam sistem ayağa qalxır

---

## API Konvensiyaları

### REST
```
Base URL:     /api/v1/{resource}
Versioning:   URL path-da (/v1, /v2)
Auth:         Authorization: Bearer <jwt>
Tenant:       JWT claim-indən götürülür (header lazım deyil)
Pagination:   ?page=0&size=20&sort=createdAt,desc
Filter:       ?from=2025-01-01&to=2025-03-31&type=EXPENSE
Response:     { "data": {...}, "meta": {...}, "error": null }
Error:        { "data": null, "error": { "code": "...", "message": "..." } }
```

### gRPC
```
Package:      efinance.{service}
Interceptor:  AuthInterceptor (hər servisə)
Error:        gRPC Status codes (UNAUTHENTICATED, NOT_FOUND, etc)
```

### Kafka Topics
```
finance.value_entry.created
finance.item_ledger.created
catalog.item.updated
storage.file.uploaded
report.export.requested
report.export.completed
```

---

## Texnologiya Stack

| Qat | Texnologiya | Versiya |
|-----|-------------|---------|
| Language | Java | 21 |
| Framework | Spring Boot | 3.5.x |
| Build | Gradle | 8.x |
| gRPC | grpc-java + spring-grpc | 1.74.0 / 0.11.0 |
| ORM | Spring Data JPA + Hibernate | 6.x |
| Security | Spring Security + JWT (jjwt) | 6.x / 0.12.x |
| Multi-tenant | Hibernate multi-tenancy (SCHEMA) | — |
| OLTP DB | PostgreSQL | 15+ |
| OLAP DB | ClickHouse | 24.x |
| Object Storage | MinIO | Latest |
| Cache | Redis | 7 |
| Message Queue | Kafka | 7.6.x |
| CDC | Debezium | 2.x |
| API Gateway | Envoy | 1.31 |
| CLI | Picocli | 4.7.x |
| Testing | JUnit 5 + Testcontainers | — |
| API Docs | Springdoc OpenAPI | 2.x |
| Rate Limit | Bucket4j | 8.7.x |

---

## Servis Dependency Xəritəsi

```
Report    →  Auth (gRPC)
Report    ←  Kafka (consumer)

Finance   →  Auth (gRPC)
Finance   →  Catalog (gRPC)
Finance   →  Notification (gRPC)
Finance   →  Kafka (producer)

Catalog   →  Auth (gRPC)
Catalog   →  Storage (gRPC)

Storage   →  Auth (gRPC)
Storage   →  MinIO

Auth      →  OTP (gRPC)
Auth      →  Notification (gRPC)
```

---

*Son yenilənmə: 2026-04-19*
