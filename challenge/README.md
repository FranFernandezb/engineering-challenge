# Tokopulse Chargeback Intelligence Service

> Solution for the **Jakarta Reversal Storm** engineering challenge — a backend
> service that ingests chargeback data from multiple payment processors,
> enriches it with original-transaction metadata, computes chargeback rates by
> dimension, and surfaces actionable alerts before Tokopulse trips the
> Visa/Mastercard excessive-chargeback thresholds.

---

## TL;DR — run the whole thing in 30 seconds

```bash
# 1. Make sure JDK 21 is on your PATH
java -version          # should print "21.x"

# 2. Start the service (downloads Maven on first run)
#    Linux / macOS:
./mvnw spring-boot:run
#    Windows (PowerShell or CMD):
mvnw.cmd spring-boot:run
```

Wait until you see `Started ChallengeApplication` and these auto-seed lines:

```
Auto-seeding demo dataset on startup...
Loaded 600 transactions from file:./data/transactions.csv
Loaded 15 ProcessorAlpha chargeback rows ...
Loaded 18 ProcessorBeta  chargeback rows ...
Loaded 4  ProcessorGamma chargeback rows ...
Auto-seed done. Transactions: 600 succeeded / 0 failed. Chargebacks — Alpha: 15, Beta: 18, Gamma: 4
```

Then open these three URLs in a browser to see everything working:

| URL | What you'll see |
| --- | --------------- |
| <http://localhost:8080/swagger-ui.html> | Full interactive API explorer |
| <http://localhost:8080/api/analytics/processors?from=2026-02-01&to=2026-05-01> | Chargeback rate per processor |
| <http://localhost:8080/api/alerts?from=2026-02-01&to=2026-05-01> | The 5 detection alerts |

> ⚠️ **Use the dates `2026-02-01` to `2026-05-01`** — the demo dataset is dated
> April–May 2026 (it's a deliberately frozen window so the data is reproducible).

---

## 1. What it does

Tokopulse routes payments through three processors. Each processor sends
chargeback notifications in a different format and on a different schedule.
This service:

1. **Ingests** chargeback files from any of the three processors via either an
   HTTP endpoint, a CSV upload, or a one-shot seed call. Each processor has its
   own normalizer that translates its native fields into a canonical schema.
2. **Enriches** every chargeback with metadata from the original transaction
   (merchant, MCC, original date / amount).
3. **Calculates** the chargeback rate broken down by processor, BIN, issuer
   country, and MCC — plus the network-style monthly trend.
4. **Detects patterns** with five independent rules (overall threshold,
   processor hotspot, BIN cluster, reason-code spike, geographic concentration)
   and returns severity-graded alerts with explanatory messages.

---

## 2. Stack

| Layer            | Choice                                          |
| ---------------- | ----------------------------------------------- |
| Language         | Java 21                                         |
| Framework        | Spring Boot                                     |
| Persistence      | Spring Data JPA + H2 in-memory (Postgres-compatible mode) |
| API docs         | springdoc-openapi (Swagger UI)                  |
| CSV parsing      | Apache Commons CSV                              |
| Build            | Maven (wrapper included)                        |

H2 in-memory was chosen so a reviewer can clone, build, and run with **zero
external setup**. A `postgres` Spring profile is included for production-style
runs (`SPRING_PROFILES_ACTIVE=postgres`).

---

## 3. Project layout

```
src/main/java/yuno/challenge/
├── ChallengeApplication.java
├── alert/                      # Pattern-detection rules + REST endpoint
│   ├── controller/AlertController.java
│   ├── dto/Alert.java, AlertReport.java
│   └── service/AlertService.java
├── analytics/                  # Rate calculation by dimension, trend, breakdowns
│   ├── controller/AnalyticsController.java
│   ├── dto/...                 # one package per metric
│   └── service/AnalyticsService.java, MccDescriptions.java
├── chargeback/                 # Chargeback ingestion + normalization
│   ├── controller/ChargebackController.java
│   ├── dto/                    # ingest request, batch result, response
│   ├── model/                  # JPA entity + enums (status, source, category)
│   ├── normalization/          # one normalizer per processor, registry
│   ├── repository/ChargebackRepository.java
│   └── service/                # ChargebackIngestionService, EnrichmentService
├── transaction/                # Original transactions
│   ├── controller/TransactionController.java
│   ├── dto/                    # request + response
│   ├── model/Transaction.java
│   ├── repository/TransactionRepository.java
│   └── service/TransactionIngestionService.java
├── seed/                       # Bulk loader for the bundled demo CSVs
│   ├── SeedController.java     # POST /api/seed/load
│   ├── SeedService.java
│   └── StartupSeeder.java      # auto-loads on first run if DB is empty
├── common/                     # ApiResponse wrapper, exceptions, handler
└── config/                     # Jackson, Security
data/
├── transactions.csv                       # 600 rows, 90-day window
├── chargebacks_processor_alpha.csv        # Visa-style codes, ISO dates
├── chargebacks_processor_beta.csv         # Mastercard codes, MM/dd/yyyy, amount in cents
├── chargebacks_processor_gamma.csv        # Internal codes, epoch dates, SCREAMING headers
└── generate_seed.py                       # Reproducible regenerator
```

---

## 4. Run it

### 4.1 Prerequisites

| Tool        | Version | How to verify              |
| ----------- | ------- | -------------------------- |
| **JDK 21**  | 21.x    | `java -version`            |
| Maven       | *not required* — the wrapper (`./mvnw` / `mvnw.cmd`) is committed | — |
| `curl`      | any     | `curl --version` (already on macOS, Linux, and Windows 10+) |

If `java -version` reports anything other than 21, install JDK 21 from
<https://adoptium.net/temurin/releases/?version=21>.

### 4.2 Start the service

From the project root (`C:\Proyectos\engineering-challenge\challenge`):

**Linux / macOS**
```bash
./mvnw spring-boot:run
```

**Windows (PowerShell or CMD)**
```cmd
mvnw.cmd spring-boot:run
```

The first run takes ~1 minute (it downloads Maven, then dependencies). When
you see `Started ChallengeApplication in N seconds (process running for N)`
the service is ready on **http://localhost:8080**.

On that same first boot the `StartupSeeder` automatically loads `/data/*.csv`
into the H2 database. Look for these lines in the log:

```
Auto-seeding demo dataset on startup...
Loaded 600 transactions from file:./data/transactions.csv
Loaded 15 ProcessorAlpha chargeback rows from file:./data/chargebacks_processor_alpha.csv
Loaded 18 ProcessorBeta  chargeback rows from file:./data/chargebacks_processor_beta.csv
Loaded 4  ProcessorGamma chargeback rows from file:./data/chargebacks_processor_gamma.csv
Auto-seed done. Transactions: 600 succeeded / 0 failed. Chargebacks — Alpha: 15, Beta: 18, Gamma: 4
```

If you don't see these lines, jump to **§7 Troubleshooting**.

### 4.3 What you have at this point

| Resource                               | URL |
| -------------------------------------- | --- |
| Swagger UI (interactive API explorer)  | <http://localhost:8080/swagger-ui.html> |
| OpenAPI spec (raw JSON)                | <http://localhost:8080/v3/api-docs> |
| H2 web console (browse tables)         | <http://localhost:8080/h2-console>  •  JDBC URL `jdbc:h2:mem:chargeback_db`, user `sa`, password *(empty)* |

To disable auto-seed (e.g. for testing manual ingestion), set
`seed.auto-load=false` in `application.yaml` or as an env var
`SEED_AUTO_LOAD=false`.

### 4.4 Manual reload (only if you disabled auto-seed)

```bash
curl -X POST http://localhost:8080/api/seed/load
```

---

## 5. The demo flow

Run these in order. Every command is followed by a snippet of the expected
response so you can verify at a glance.

### 5.1 Sanity check — ingestion worked

```bash
curl http://localhost:8080/api/transactions/count
```
Expected:
```json
{"success":true,"data":{"transactions":600},"timestamp":"..."}
```

```bash
curl http://localhost:8080/api/chargebacks/CB-00001
```
Expected — a fully enriched record with merchant, MCC, and original txn date
attached:
```json
{
  "success": true,
  "data": {
    "chargebackId": "CB-00001",
    "transactionId": "TXN-00223",
    "processorName": "ProcessorAlpha",
    "amount": 320492.46,
    "currency": "IDR",
    "reasonCode": "10.1",
    "reasonCategory": "FRAUD",
    "merchantId": "MERCH-002",
    "merchantName": "Tokopulse Marketplace",
    "mcc": "5816",
    ...
  }
}
```

### 5.2 Chargeback rates by dimension (Core Requirement 2)

Each endpoint returns the breakdown **plus a `ratePercent` per row** computed
as `(chargebacks / transactions) × 100`.

```bash
# By processor
curl "http://localhost:8080/api/analytics/processors?from=2026-02-01&to=2026-05-01"
```
Expected (abridged):
```json
{
  "overallRatePercent": 6.1667,
  "processors": [
    {"processorName":"ProcessorAlpha","chargebackCount":15,"transactionCount":319,"ratePercent":4.7022,"totalAmount":...},
    {"processorName":"ProcessorBeta", "chargebackCount":18,"transactionCount":134,"ratePercent":13.4328,"totalAmount":...},
    {"processorName":"ProcessorGamma","chargebackCount":4, "transactionCount":147,"ratePercent":2.7211,"totalAmount":...}
  ]
}
```

```bash
# By BIN (and country) — each carries its own ratePercent
curl "http://localhost:8080/api/analytics/bins?from=2026-02-01&to=2026-05-01"

# By MCC
curl "http://localhost:8080/api/analytics/mcc?from=2026-02-01&to=2026-05-01"

# By reason code (with normalized category + share %)
curl "http://localhost:8080/api/analytics/reason-codes?from=2026-02-01&to=2026-05-01"

# Monthly rate trend (Visa/MC methodology: CBs in M ÷ Txns in M-1)
curl "http://localhost:8080/api/analytics/rate?from=2026-02-01&to=2026-05-01"
```

### 5.3 Pattern-detection alerts (Core Requirement 3)

```bash
curl "http://localhost:8080/api/alerts?from=2026-02-01&to=2026-05-01"
```

You should see **five alerts** in the response (sorted CRITICAL → WARNING):

| Rule ID                  | Severity  | Triggered by |
| ------------------------ | --------- | ------------ |
| `OVERALL_RATE_CRITICAL`  | CRITICAL  | Overall rate ≈ **6.17%** (over the 1.5% network limit) |
| `PROCESSOR_HOTSPOT`      | CRITICAL  | ProcessorBeta @ **13.43%** = **2.18× portfolio average** |
| `GEO_CONCENTRATION`      | CRITICAL  | Country **NG = 48.6%** of chargebacks but only **7.5%** of transactions |
| `BIN_CLUSTER`            | WARNING   | **12 chargebacks** all from BIN `453210` |
| `REASON_CODE_SPIKE`      | WARNING   | **86.5%** of disputes are FRAUD-category |

Sample alert from the response:
```json
{
  "ruleId": "PROCESSOR_HOTSPOT",
  "dimension": "PROCESSOR",
  "severity": "CRITICAL",
  "message": "ProcessorBeta chargeback rate is 13.4328% — 2.18x the portfolio average of 6.1667%. Investigate routing or fraud patterns specific to this processor.",
  "metricValue": 13.4328,
  "threshold": 12.3334,
  "details": {"processor":"ProcessorBeta","chargebacks":18,"transactions":134, ...}
}
```

### 5.4 Ingest a brand-new chargeback (Core Requirement 1)

Demonstrates the per-processor normalization. **ProcessorAlpha** payload:

```bash
curl -X POST http://localhost:8080/api/chargebacks/ingest \
  -H "Content-Type: application/json" \
  -d '{
        "processorName": "ProcessorAlpha",
        "fields": {
          "dispute_reference_number": "CB-DEMO-001",
          "original_txn_id": "TXN-00001",
          "dispute_amount": "299000",
          "dispute_currency": "IDR",
          "visa_reason_code": "10.4",
          "dispute_date": "2026-04-15",
          "card_bin": "453210",
          "card_issuer_country": "ID"
        }
      }'
```

Same chargeback in **ProcessorBeta**'s native format (camelCase, amount in cents,
MM/dd/yyyy):

```bash
curl -X POST http://localhost:8080/api/chargebacks/ingest \
  -H "Content-Type: application/json" \
  -d '{
        "processorName": "ProcessorBeta",
        "fields": {
          "cbId": "CB-DEMO-002",
          "txnReference": "TXN-00002",
          "amountCents": "29900000",
          "currencyCode": "IDR",
          "reasonCode": "4837",
          "chargebackDate": "04/15/2026",
          "binNumber": "453210",
          "issuingCountry": "ID"
        }
      }'
```

Both calls return a 201 with the same canonical schema — the normalizers do
the format translation.

### 5.5 Upload a CSV (any processor)

```bash
curl -X POST "http://localhost:8080/api/chargebacks/upload?processorName=ProcessorAlpha" \
  -F "file=@data/chargebacks_processor_alpha.csv"
```

(On Windows CMD use `-F "file=@data\\chargebacks_processor_alpha.csv"`.)

Returns a batch result showing total / succeeded / failed / duplicates. Since
auto-seed already loaded these rows, you'll see all 15 counted as `duplicates` —
which proves the idempotency check works.

---

## 6. Tying back to the rubric

| Rubric item                                         | Where to look                                                                 |
| --------------------------------------------------- | ----------------------------------------------------------------------------- |
| **CR1** — Multi-source ingestion (≥3 processors)    | §5.4 (3 different payloads); `chargeback/normalization/Processor*Normalizer.java` |
| **CR2** — Enrichment + rates across ≥3 dimensions   | §5.1 (enriched lookup); §5.2 (4 dimensions: processor, BIN, country, MCC)     |
| **CR3** — ≥2 distinct, meaningful pattern alerts    | §5.3 (5 distinct rules, all firing on demo data)                              |
| **Code quality**                                    | Layered packages (`alert`/`analytics`/`chargeback`/`transaction`/`seed`/`common`); strategy + registry pattern for normalizers; global exception handler; bean validation |
| **Documentation & demo**                            | This README + Swagger UI + bundled CSVs + reproducible Python generator       |
| **Stretch goals**                                   | Reason-code normalization to canonical categories (`ReasonCategory.fromCode`); REST API + OpenAPI/Swagger; H2 console; idempotent ingestion; monthly trend with Visa/MC methodology |

---

## 7. Troubleshooting

| Symptom | Fix |
| ------- | --- |
| `mvnw: command not found` (Linux/macOS) | `chmod +x mvnw && ./mvnw spring-boot:run` |
| `'mvnw.cmd' is not recognized` (Windows) | Run from the project root, not from `src/`. Use `.\mvnw.cmd spring-boot:run` in PowerShell. |
| `java -version` reports 11 / 17 | Install JDK 21 (Temurin) and ensure `JAVA_HOME` points to it. |
| Port 8080 already in use | Start with `--server.port=8081` (`./mvnw spring-boot:run -Dspring-boot.run.arguments=--server.port=8081`). |
| Auto-seed didn't run | Hit `POST /api/seed/load` manually, or check the working directory — the seeder resolves `./data/*.csv` relative to where you launched the JVM. Always run from the project root. |
| Empty results from analytics endpoints | The dataset is dated **2026-02-01 → 2026-05-01** — use those dates in `from`/`to`, not today's date. |
| H2 console asks for a password | Leave the password field empty. JDBC URL: `jdbc:h2:mem:chargeback_db`. |

---

## 8. API surface

| Method | Path | Purpose |
| ------ | ---- | ------- |
| POST   | `/api/transactions`                | Ingest a single transaction |
| POST   | `/api/transactions/batch`          | Ingest a JSON array of transactions |
| POST   | `/api/transactions/upload`         | Upload a transaction CSV |
| GET    | `/api/transactions/count`          | Health-check count |
| POST   | `/api/chargebacks/ingest`          | Ingest a single chargeback (any processor) |
| POST   | `/api/chargebacks/ingest/batch`    | Ingest a JSON batch (mixed processors allowed) |
| POST   | `/api/chargebacks/upload`          | Upload a chargeback CSV (`processorName` query param) |
| GET    | `/api/chargebacks/{chargebackId}`  | Fetch one chargeback (with enrichment) |
| GET    | `/api/analytics/processors`        | Chargeback count + rate per processor |
| GET    | `/api/analytics/bins`              | BIN cluster + country breakdown with rates |
| GET    | `/api/analytics/reason-codes`      | Raw codes + normalized categories + share % |
| GET    | `/api/analytics/mcc`               | Per-MCC volume + rate |
| GET    | `/api/analytics/rate`              | Monthly trend with Visa/MC methodology |
| GET    | `/api/alerts`                      | Run all detection rules and return the report |
| POST   | `/api/seed/load`                   | Reload the bundled `/data` CSVs |

All endpoints accept `from` / `to` as ISO dates (`yyyy-MM-dd`).

A live Swagger UI is at **<http://localhost:8080/swagger-ui.html>** with
schemas, examples, and a "try it out" form per endpoint.

---

## 9. Pattern-detection rules (the alerting engine)

Implemented in `alert/service/AlertService.java`. Each rule is independent and
returns zero or more `Alert` objects; the controller aggregates them into a
sorted `AlertReport`.

| Rule ID | Severity | Trigger | Why it matters |
| ------- | -------- | ------- | -------------- |
| `OVERALL_RATE_WARNING`  | WARNING  | overall rate ≥ 1.0% | Early warning before the network limit |
| `OVERALL_RATE_CRITICAL` | CRITICAL | overall rate ≥ 1.5% | Visa/Mastercard excessive chargeback program threshold |
| `PROCESSOR_HOTSPOT`     | CRITICAL | processor rate ≥ 2× portfolio average | Routing or processor-specific fraud |
| `BIN_CLUSTER`           | WARNING  | ≥ 8 chargebacks from one BIN | Compromised card range / fraud-prone issuer |
| `REASON_CODE_SPIKE`     | WARNING  | one category ≥ 60% of disputes | Concentrated root cause (fraud, fulfillment, etc.) |
| `GEO_CONCENTRATION`     | CRITICAL | country ≥ 40% chargebacks but ≤ 20% of transactions | Geographic fraud signal disproportionate to volume |

All thresholds are constants at the top of `AlertService` — easy to make
configurable per-merchant later.

---

## 10. Reason-code normalization

Each processor uses a different reason-code dictionary (Visa `10.4`, Mastercard
`4837`, internal `PB-FRAUD-01`, etc.). The `ReasonCategory.fromCode()` mapper
in `chargeback/model/` collapses them into five canonical buckets:

* `FRAUD`
* `FULFILLMENT`
* `PROCESSING_ERROR`
* `AUTHORIZATION`
* `RECURRING_BILLING`
* `OTHER` (fallback for unknown codes)

The category is persisted alongside the raw code so reports stay comparable
across processors.

---

## 11. Test data

Generated with `data/generate_seed.py` (deterministic — same output every run).

* **600 transactions** over a 90-day window ending 2026-05-01.
* **37 chargebacks** distributed across 3 processor formats.
* Engineered patterns to ensure every alert rule fires; see the table in §5.3.

To regenerate:

```bash
python3 data/generate_seed.py
```

---

## 12. Architectural notes & trade-offs

* **Strategy + registry for normalization.** Every processor is a
  `ProcessorNormalizer` bean; the `ProcessorNormalizerRegistry` autowires them
  into a name → strategy map at startup. Adding a new processor is just one new
  class — no changes to the ingestion service or controllers.
* **Two-step persist for enrichment.** The chargeback is saved first (so the
  audit trail is preserved even if enrichment fails), then enriched and saved
  again. Tradeoff: two writes per ingest, but no lost data if the original
  transaction isn't yet in the system (out-of-order ingestion is common in
  payments).
* **Idempotency key.** `processor::chargebackId` — re-ingestion of the same
  webhook returns 409 instead of duplicating rows.
* **Two rate methodologies.**
  * Per-dimension breakdowns use *(chargebacks in window) / (transactions in
    same window)* — simpler and matches what an operator wants when slicing.
  * The trend endpoint uses *(chargebacks in month X) / (transactions in
    month X-1)* — Visa/Mastercard methodology, accounting for the 30–120-day
    chargeback filing lag.
* **H2 default, Postgres optional.** Reviewer-friendly out of the box; one env
  var flip to switch to the production-style profile.
* **Spring Security included but permit-all.** A real Yuno deployment would
  back this with JWT/OAuth2; the demo opens everything so reviewers don't fight
  authentication. Config is one bean (`config/SecurityConfig.java`) — easy to
  replace.
* **Validation.** Bean Validation (`@NotBlank`, `@Size`, `@Positive`) on the
  request DTOs, plus per-processor field-level validation in normalizers. A
  global `@RestControllerAdvice` returns structured `ApiResponse` errors for
  every exception type.

### Things I would add with more time

* Spring profiles for per-merchant alert thresholds (currently hard-coded).
* Time-series persistence + week-over-week deltas (the "stretch goal").
* Dispute-evidence recommendations driven off `ReasonCategory`.
* WebSocket/SSE alert push so the risk team gets notifications instead of
  having to poll `/api/alerts`.
* Integration tests with `@SpringBootTest` exercising the full ingest →
  enrich → analyze → alert path.

---

## 13. Deliverables checklist

| Item                                  | Where |
| ------------------------------------- | ----- |
| Source code                           | `src/main/java/yuno/challenge/` |
| Setup / run instructions              | this README, §4 |
| How to load test datasets             | this README, §4 (auto) and §4.4 (manual) |
| How to query metrics                  | this README, §5.2 |
| How to trigger pattern detection      | this README, §5.3 |
| Test datasets                         | `data/` (CSVs + generator) |
| API documentation                     | Swagger UI at `/swagger-ui.html`, plus this README §8 |
| Architecture & trade-offs             | this README, §12 |
| Demo evidence                         | the `curl` walkthrough in §5 — every command produces verifiable output against the bundled dataset; rubric mapping in §6 |
