# Companies Data Form Expansion Module — Specification

> **UPDATE 2026-03-10:** The Professional Character Declaration (JDG consent) has been **removed**.
> Under Polish law (Art. 38 Consumer Rights Act), we cannot force a statement that the purchase
> is for company use only. Instead, a "Consent to Immediate Commencement of Service"
> (`SUBSCRIPTION_ACTIVATION_CONSENT`) will be collected at **subscription purchase time**
> (future Stripe module). Account activation now requires only: email verified + company data filled.
> See migration: `11-03-2026-replace-pcd-with-subscription-consent.sql`

## 1. Overview

Expand the company registration form to collect verified business data from Polish public registries, enabling:

- **Auto-population** of company data by NIP (tax identification number).
- **Company type classification** (JDG, sp. z o.o., S.A., etc.).
- **Professional character declaration** for JDG sole proprietors.
- **Auto-activation** of company accounts after data verification + email confirmation.
- **Invoice-ready data** stored for future Stripe billing integration.

### Business Model

- **B2B only** — the platform is exclusively for professional business use in marketing/brand collaboration.
- Registration requires a valid NIP — this inherently filters out consumers.
- The platform exercises its right under **Art. 353¹ of the Polish Civil Code** (freedom of contract) to choose its contracting partners and restrict service to registered businesses.

### Billing & Refund Policy

- **Subscription model**, billed monthly in advance.
- **No refunds** for already-billed periods — enforced via Terms of Service.
- **No 14-day withdrawal right** — this is a consumer protection (prawo odstąpienia) that does not apply to B2B contracts.
- **Not a VAT payer yet** — invoices issued without VAT for now. Company data is collected in advance so invoices can be generated correctly when VAT registration occurs.

### Legal Basis for No Withdrawal Right

- **Sp. z o.o., S.A., other KRS-registered entities**: never consumers under Polish law. No withdrawal right, period.
- **JDG sole proprietors**: since the 2021 amendment (and further clarified from autumn 2025), JDGs can claim consumer-like protections — **but only** when the contract lacks professional character ("charakter zawodowy") for them. Since our platform provides marketing/brand collaboration services, and we require an explicit declaration of professional character during registration, the withdrawal right does not apply.
- **If a JDG declares the contract is NOT professional** → registration is blocked. We do not offer services to non-professional users. This is a legitimate business decision under freedom of contract (Art. 353¹ KC), not discrimination.

---

## 2. Company Types

### Classification

| Type                | Registry | Legal Form                                           | Consumer Risk |
|---------------------|----------|------------------------------------------------------|---------------|
| **JDG**             | CEIDG    | Jednoosobowa działalność gospodarcza (sole proprietor) | Requires professional character declaration |
| **Sp. z o.o.**      | KRS      | Spółka z ograniczoną odpowiedzialnością (LLC)         | None          |
| **S.A.**            | KRS      | Spółka akcyjna (joint-stock company)                  | None          |
| **Sp. k.**          | KRS      | Spółka komandytowa (limited partnership)              | None          |
| **Sp. j.**          | KRS      | Spółka jawna (general partnership)                    | None          |
| **Other KRS**       | KRS      | Other registered entities                             | None          |

### Detection Logic

After the user enters their NIP:
1. Query GUS BIR1 → get legal form code.
2. If legal form indicates sole proprietorship (JDG) → flag as `CEIDG` type → require professional character declaration.
3. If legal form indicates any KRS-registered entity → flag as `KRS` type → no declaration needed.

---

## 3. Architecture Decision: Registry API Selection

### Evaluated Options

| Option | Protocol | Cost | Fields Returned | Verdict |
|--------|----------|------|-----------------|---------|
| **GUS BIR 1.1** (official) | SOAP | Free | ALL (legal form, PKD, dates, status, full address, KRS) | **SELECTED** |
| **DataPort.pl** | REST | 299 PLN/yr (Pro) | Only 8 fields (name, NIP, REGON, address) — **missing legal form, PKD, status** | **REJECTED** — cannot determine JDG vs KRS |
| **nip24.pl** | REST | Expensive (tiered) | All fields, Java client on Maven | **REJECTED** — unnecessary cost, GUS is free |
| **Transparent Data** | REST | Enterprise pricing | Comprehensive | **REJECTED** — enterprise pricing, overkill |

### Decision: GUS BIR 1.1 Direct Integration

**Rationale**: GUS BIR 1.1 is the authoritative source. It's free, returns every field we need, and all paid alternatives are just wrappers around it. The SOAP complexity is a one-time cost handled by existing Java libraries.

**DataPort.pl specifically rejected** because it returns only basic identification (NIP, REGON, name) and a partial address breakdown. It does not expose legal form codes (`praw_podstawowaFormaPrawna_Symbol`), making JDG vs KRS determination impossible. At 299 PLN/yr for 300 queries/day, it provides less data than the free GUS API.

### Adapter Pattern Requirement

All registry access MUST be implemented behind interfaces (port/adapter pattern). Each data source is a swappable connector:

```
CompanyRegistryPort (interface)
  ├── GusBir1RegistryAdapter    ← current implementation (SOAP)
  └── [future: any REST adapter if a better paid API emerges]

VatRegistryPort (interface)
  └── BialaListaVatAdapter      ← current implementation (REST, free)

SoleProprietorRegistryPort (interface)
  └── CeidgRegistryAdapter      ← current implementation (REST)
```

This allows swapping GUS SOAP for any future REST-based alternative by implementing a new adapter and changing the Spring `@Profile` or `@ConditionalOnProperty` — zero changes to business logic.

---

## 4. Polish Public Registry APIs

Three free public APIs provide the data needed for company verification and invoice generation.

### 4.1 GUS BIR 1.1 (REGON Database) — Primary

**Purpose**: Primary data source — company lookup by NIP. Returns ALL base data including legal form (JDG vs KRS determination).

| Detail              | Value                                                                     |
|---------------------|---------------------------------------------------------------------------|
| **Protocol**        | SOAP (BIR 1.1)                                                           |
| **Production URL**  | `https://wyszukiwarkaregon.stat.gov.pl/wsBIR/UslugaBIRzewnPubl.svc`     |
| **Production WSDL** | `https://wyszukiwarkaregon.stat.gov.pl/wsBIR/wsdl/UslugaBIRzewnPubl-ver11-prod.wsdl` |
| **Test URL**        | `https://wyszukiwarkaregontest.stat.gov.pl/wsBIR/UslugaBIRzewnPubl.svc` |
| **Test WSDL**       | `https://wyszukiwarkaregontest.stat.gov.pl/wsBIR/wsdl/UslugaBIRzewnPubl-ver11-test.wsdl` |
| **Test API key**    | `abcde12345abcde12345` (public, anonymized data)                         |
| **Auth**            | API key (free, requested via `regon_bir@stat.gov.pl`) — **requested, awaiting production key** |
| **Rate limit**      | 6,000/hour, 120/minute, 3/second (peak hours 8:00-16:59)                |
| **Key methods**     | `DaneSzukajPodmioty` (search by NIP) → `DanePobierzPelnyRaport` (full report) |

**Two-step lookup flow**:
1. `DaneSzukajPodmioty` with NIP → returns basic result with REGON, entity type
2. `DanePobierzPelnyRaport` with REGON + report type → returns full data

**Report types**:
- `BIR11OsPrawna` — legal entities (sp. z o.o., S.A., etc.) — fields prefixed `praw_`
- `BIR11OsFizycznaDzwordsalalnosc` — sole proprietors (JDG) — fields prefixed `fiz_`
- `BIR11OsPrawnaPkd` / `BIR11OsFizycznaPkd` — PKD codes (separate report)

**Key fields returned** (full report):

| Field | GUS BIR 1.1 Name | Purpose |
|-------|-------------------|---------|
| Company name | `praw_nazwa` / `fiz_nazwa` | Display + invoice |
| Short name | `praw_nazwaSkrocona` | Display |
| NIP | `praw_nip` | Confirmed |
| REGON | `praw_regon9` | Identification |
| Legal form (basic) | `praw_podstawowaFormaPrawna_Symbol` | **JDG detection**: "9" = JDG, "1" = legal person, "2" = org unit |
| Legal form (specific) | `praw_szczegolnaFormaPrawna_Symbol` | **Company type**: "117" = sp. z o.o., "116" = S.A., etc. |
| Registry type | `praw_rodzajRejestruEwidencji_Nazwa` | "REJESTR PRZEDSIEBIORCOW" (KRS) or "CEIDG" |
| KRS number | `praw_numerWRejestrzeEwidencji` | For KRS entities |
| Street | `praw_adSiedzUlica_Nazwa` | Address |
| Building | `praw_adSiedzNumerNieruchomosci` | Address |
| Apartment | `praw_adSiedzNumerLokalu` | Address |
| City | `praw_adSiedzMiejscowosc_Nazwa` | Address |
| Postal code | `praw_adSiedzKodPocztowy` | Address |
| Voivodeship | `praw_adSiedzWojewodztwo_Nazwa` | Address |
| Activity start date | `praw_dataRozpoczeciaDzialalnosci` | Verification |
| Activity end date | `praw_dataZakonczeniaDzialalnosci` | Status (if set → inactive) |
| Suspension date | `praw_dataZawieszeniaDzialalnosci` | Status |
| Bankruptcy date | `praw_dataOrzeczeniaOUpadlosci` | Status |
| PKD codes | `praw_pkdKod` + `praw_pkdPrzewazajace` | Business classification (separate report) |

**Implementation**: Use `eximius313/bir1-api` Java library from GitHub, or generate classes from WSDL.

### 4.2 Ministry of Finance — Biała Lista (White List)

**Purpose**: VAT status verification + registered bank account validation.

| Detail              | Value                                                               |
|---------------------|---------------------------------------------------------------------|
| **Protocol**        | REST                                                                |
| **Base URL**        | `https://wl-api.mf.gov.pl`                                         |
| **Auth**            | None (public)                                                       |
| **Rate limit**      | 300 queries/day                                                     |
| **Key endpoints**   | `/api/search/nip/{nip}?date={yyyy-MM-dd}`                          |

**Data returned**:
- VAT taxpayer status: `active`, `exempt`, `deregistered`, `unregistered`
- Registered bank account numbers (for future invoice payment verification)
- Company name and address (cross-validation with GUS)
- Registration/deregistration dates

**Important for invoicing**: when the platform becomes a VAT payer, this API verifies that payments go to accounts on the White List (mandatory for VAT deduction under Polish tax law).

### 4.3 CEIDG (Sole Proprietorship Registry)

**Purpose**: Additional data for JDG companies — confirms sole proprietorship status, PKD codes, activity dates, owner name.

| Detail              | Value                                                               |
|---------------------|---------------------------------------------------------------------|
| **Protocol**        | REST (API v2)                                                       |
| **Base URL**        | `https://dane.biznes.gov.pl/api/ceidg/v2`                          |
| **Test URL**        | `https://test-dane.biznes.gov.pl/api/ceidg/v2`                     |
| **Auth**            | Bearer token (API key from biznes.gov.pl portal via Profil Zaufany) |
| **API key status**  | **Ready** — stored in `.env` as `CEIDG_APP_KEY`                    |
| **Rate limit**      | Fair-use                                                            |

**Data returned**:
- Owner's full name
- Business name
- Registered and correspondence address
- PKD codes
- Business start/end/suspension dates
- Status (active, suspended, liquidated, deleted)

### API Usage Strategy

| Step | API         | Purpose                                              | Status |
|------|-------------|------------------------------------------------------|--------|
| 1    | GUS BIR1    | Primary lookup by NIP — get all base data            | Test key ready, production key requested |
| 2    | Biała Lista | VAT status check — active/exempt/deregistered        | No key needed (public) |
| 3    | CEIDG       | Only for JDG — cross-validate, get owner name        | Production key ready (`CEIDG_APP_KEY`) |

Step 3 is conditional — only called when GUS BIR1 identifies the entity as a sole proprietorship (legal form symbol "9").

### API Key Configuration

```properties
# .env / application-*.yml
gus.bir1.api-key=${GUS_BIR1_API_KEY:abcde12345abcde12345}  # test key as default
gus.bir1.environment=test  # switch to "production" when prod key arrives
ceidg.api-key=${CEIDG_APP_KEY}
# Biała Lista: no key needed
```

---

## 4. Registration Flow (Expanded)

### 4.1 Company Data Entry

1. User enters **NIP** on the registration form.
2. FE calls BE endpoint → BE queries GUS BIR1 + Biała Lista (+ CEIDG if JDG).
3. BE returns the fetched data to FE.
4. FE **auto-populates** the form fields with the registry data.
5. User reviews and confirms the data (can correct correspondence address if different from registered address).

### 4.2 Form Fields

| Field                     | Source          | Editable | Required |
|---------------------------|-----------------|----------|----------|
| NIP                       | User input      | Yes      | Yes      |
| Company name              | GUS BIR1        | No       | Yes      |
| REGON                     | GUS BIR1        | No       | Yes      |
| Legal form                | GUS BIR1        | No       | Yes      |
| Company type (JDG/KRS)    | Derived         | No       | Yes      |
| Registered address        | GUS BIR1        | No       | Yes      |
| Correspondence address    | User input      | Yes      | No       |
| PKD codes                 | GUS BIR1        | No       | Yes      |
| VAT status                | Biała Lista     | No       | Yes      |
| Bank account (primary)    | Biała Lista     | No       | No       |
| Owner name (JDG only)     | CEIDG           | No       | For JDG  |
| Contact email             | User input      | Yes      | Yes      |
| Contact phone             | User input      | Yes      | Yes      |

### 4.3 Professional Character Declaration (JDG Only)

If the company type is JDG, display an additional **mandatory checkbox**:

> *"Oświadczam, że niniejsza umowa ma dla mnie charakter zawodowy, bezpośrednio związany z prowadzoną przeze mnie działalnością gospodarczą (Art. 385⁵ Kodeksu cywilnego)."*
>
> (I declare that this contract has professional character for me, directly related to my business activity.)

- **Checked** → proceed with registration.
- **Not checked** → registration is blocked with a message: *"Our platform is available exclusively for professional business use. If this service is not related to your professional activity, we are unable to provide it."*

### Unified with Consent Module

The professional character declaration is handled as a standard legal document in the existing consent module infrastructure:

- **`legal_document` table**: new type `PROFESSIONAL_CHARACTER_DECLARATION`, with PL and EN versions, versioned PDFs in GCS, SHA-256 hash.
- **`consent_record` table**: acceptance stored with the same proof bundle as other consents (timestamp, `isTrusted`, userAgent, IP, `documentHash`).
- **HMAC cookie**: stored in `consent_professional_character` cookie during registration (same mechanism as ToS/Privacy Policy cookies from Consent Module section 3).
- **Versioning**: follows the same 2-language, versioned PDF pattern. When the declaration text changes, a new version is published — but since this only applies at registration time (not ongoing use), no re-consent flow is needed for existing users.

This means 4 document types total in the consent module:

| Type                                | Applies to       | When                  |
|-------------------------------------|------------------|-----------------------|
| `COOKIE_POLICY`                     | All users        | Cookie banner         |
| `TERMS_OF_SERVICE`                  | All users        | Registration          |
| `PRIVACY_POLICY`                    | All users        | Registration          |
| `PROFESSIONAL_CHARACTER_DECLARATION`| JDG companies    | Registration only     |

### 4.4 Auto-Activation

After successful registration:
1. Company data is saved.
2. Email confirmation is sent (existing mechanism).
3. After email is confirmed → account status transitions to `ACTIVE`.

No manual admin approval needed — the registry data verification serves as the validation step.

---

## 5. Database Design

### 5.1 `company_data` Table (New or Extended)

| Column                    | Type        | Description                                                  |
|---------------------------|-------------|--------------------------------------------------------------|
| `id`                      | BIGINT PK   | Auto-generated                                               |
| `user_id`                 | BIGINT FK   | References `users.id` (the company user)                     |
| `nip`                     | VARCHAR(10) | Tax identification number (unique)                           |
| `regon`                   | VARCHAR(14) | REGON number                                                 |
| `company_name`            | VARCHAR     | Full legal name from registry                                |
| `company_type`            | VARCHAR     | Enum: `JDG`, `SP_ZOO`, `SA`, `SP_K`, `SP_J`, `OTHER_KRS`   |
| `legal_form_code`         | VARCHAR     | GUS legal form code (raw)                                    |
| `registered_address`      | JSONB       | `{ street, building, apartment, city, postalCode, voivodeship }` |
| `correspondence_address`  | JSONB       | User-provided, nullable                                      |
| `pkd_codes`               | JSONB       | Array of PKD codes from registry                             |
| `vat_status`              | VARCHAR     | `ACTIVE`, `EXEMPT`, `DEREGISTERED`, `UNREGISTERED`           |
| `bank_accounts`           | JSONB       | Array of registered bank accounts from Biała Lista           |
| `owner_name`              | VARCHAR     | Full name of owner (JDG only, nullable)                      |
| `registry_data_fetched_at`| TIMESTAMP   | When the data was last fetched from registries               |
| `created_at`              | TIMESTAMP   | When the record was created                                  |
| `updated_at`              | TIMESTAMP   | Last modification                                            |

**Unique constraint**: `nip`

### 5.2 Professional Character Declaration

Fully unified with the consent module (see section 4.3):
- `legal_document` entry with type `PROFESSIONAL_CHARACTER_DECLARATION`, PL + EN versions, SHA-256 hash.
- `consent_record` with `source = REGISTRATION`, same proof bundle as all other consents.
- 4th HMAC cookie (`consent_professional_character`) during registration flow.

---

## 6. Backend Implementation

### 6.1 Package Structure

New package: `com.sm.instagram.platform.registry`

```
registry/
  ├── RegistryLookupService.java          — orchestrator (injects ports)
  ├── CompanyRegistryData.java            — unified DTO returned to controller
  ├── RegistryController.java             — POST /api/registry/lookup
  ├── CompanyType.java                    — enum: JDG, SP_ZOO, SA, SP_K, SP_J, OTHER_KRS
  ├── port/
  │   ├── CompanyRegistryPort.java        — interface for company data lookup
  │   ├── VatRegistryPort.java            — interface for VAT status lookup
  │   └── SoleProprietorRegistryPort.java — interface for JDG owner data
  ├── adapter/
  │   ├── gus/
  │   │   ├── GusBir1RegistryAdapter.java — implements CompanyRegistryPort (SOAP)
  │   │   ├── GusBir1Config.java          — SOAP client configuration
  │   │   └── GusBir1ResponseMapper.java  — maps GUS XML to CompanyRegistryData
  │   ├── bialista/
  │   │   └── BialaListaVatAdapter.java   — implements VatRegistryPort (REST)
  │   └── ceidg/
  │       └── CeidgRegistryAdapter.java   — implements SoleProprietorRegistryPort (REST)
  └── CompanyData.java                    — JPA entity for company_data table
```

### 6.2 Port Interfaces

```java
public interface CompanyRegistryPort {
    CompanyRegistryData lookupByNip(String nip);
}

public interface VatRegistryPort {
    VatStatusData lookupVatStatus(String nip);
}

public interface SoleProprietorRegistryPort {
    SoleProprietorData lookupByNip(String nip);
}
```

Each port has exactly one implementation today. To swap GUS SOAP for a future REST API, implement `CompanyRegistryPort` in a new adapter class and activate it via `@ConditionalOnProperty("registry.company.provider")`.

### 6.3 `RegistryLookupService` (Orchestrator)

```
lookupByNip(nip: String) → CompanyRegistryData
```

1. Call `companyRegistryPort.lookupByNip(nip)` → base company data (GUS BIR 1.1).
2. Call `vatRegistryPort.lookupVatStatus(nip)` → VAT status + bank accounts (Biała Lista).
3. If JDG → call `soleProprietorRegistryPort.lookupByNip(nip)` → owner name (CEIDG).
4. Merge results into `CompanyRegistryData` DTO.

### 6.4 API Key Management

| API | Key Source | Config Property | Status |
|-----|-----------|-----------------|--------|
| GUS BIR 1.1 | `regon_bir@stat.gov.pl` (email request) | `gus.bir1.api-key` | Test key active, production key requested |
| CEIDG | biznes.gov.pl portal (Profil Zaufany) | `ceidg.api-key` / `CEIDG_APP_KEY` | **Production key ready** |
| Biała Lista | None needed | — | Public API |

### 6.3 Error Handling

| Scenario                          | Response                                                     |
|-----------------------------------|--------------------------------------------------------------|
| NIP not found in GUS              | `error.registry.nip_not_found`                               |
| Company is liquidated/inactive    | `error.registry.company_inactive`                            |
| GUS API unavailable               | `error.registry.service_unavailable` — allow manual retry    |
| Biała Lista daily limit reached   | Log warning, proceed without VAT data (non-blocking)         |
| NIP already registered            | `error.registry.nip_already_registered`                      |

### 6.4 Endpoint

```
POST /api/registry/lookup
Body: { "nip": "1234567890" }
Response: CompanyRegistryData (all fields from section 4.2)
```

Authenticated — user must be logged in (during registration flow, after Firebase account creation but before full account activation).

---

## 7. Invoice Data Readiness

Although the platform is not yet a VAT payer, all data needed for future invoice generation is collected and stored:

| Invoice Field            | Source                        |
|--------------------------|-------------------------------|
| Seller data              | Platform's own business data  |
| Buyer NIP                | `company_data.nip`            |
| Buyer name               | `company_data.company_name`   |
| Buyer address            | `company_data.registered_address` |
| VAT status               | `company_data.vat_status`     |
| Bank account (for payment verification) | `company_data.bank_accounts` |

When VAT registration happens:
- Invoices can be auto-generated from stored data.
- Biała Lista bank account verification ensures tax-deductible payments.
- KSeF (Krajowy System e-Faktur) integration can be added later using the same data.

---

## 8. Data Refresh

Company data can become stale (address changes, VAT status changes, PKD updates).

### Strategy

- **On-demand refresh**: company user can trigger a data refresh from their settings page. BE re-queries all three APIs and updates `company_data`.
- **Pre-invoice refresh**: before generating an invoice, re-verify NIP + VAT status from Biała Lista (low cost, no auth needed, critical for VAT correctness).
- **No automatic periodic refresh** for now — unnecessary with a small user base.

---

## 9. Edge Cases

### 9.1 Company Registered with Multiple NIPs

Rare but possible (e.g., branches). The primary NIP is used. If needed, additional NIPs can be handled in a future iteration.

### 9.2 JDG Refusing Professional Character Declaration

Registration is blocked. Clear message explaining the platform is B2B-only. No workaround — this is by design.

### 9.3 GUS API Downtime

GUS BIR1 has occasional maintenance windows. If unavailable:
- Show error with retry option.
- Do not allow manual data entry as a fallback — registry verification is mandatory for auto-activation.

### 9.4 NIP Validation

Validate NIP format client-side before API call (10 digits, checksum algorithm). Reject obviously invalid NIPs without hitting the API.

### 9.5 Company Data Changes After Registration

If a company changes its legal name, address, or VAT status after registration:
- Data refresh (section 8) handles this.
- Changes to legal form (e.g., JDG converting to sp. z o.o.) may require re-registration or a manual admin review.

---

## 10. Summary of Changes

| Area                      | Change                                                                    |
|---------------------------|---------------------------------------------------------------------------|
| New package               | `com.sm.instagram.platform.registry` — port/adapter architecture         |
| New interfaces (ports)    | `CompanyRegistryPort`, `VatRegistryPort`, `SoleProprietorRegistryPort`   |
| New adapters              | `GusBir1RegistryAdapter` (SOAP), `BialaListaVatAdapter` (REST), `CeidgRegistryAdapter` (REST) |
| New orchestrator          | `RegistryLookupService` — coordinates all three ports                    |
| New table                 | `company_data` — full company registry data with JSONB fields            |
| New entity                | `CompanyData` — JPA entity with `CompanyType` enum                       |
| New endpoint              | `POST /api/registry/lookup` — NIP-based company data fetch               |
| Registration form (FE)    | Auto-populated fields, NIP input, company type display                    |
| JDG checkbox              | Professional character declaration (mandatory for sole proprietors)       |
| Consent module            | `PROFESSIONAL_CHARACTER_DECLARATION` already in enum — seed documents needed |
| Auto-activation           | Email confirmed + registry data verified → `ACTIVE`                      |
| Config                    | `gus.bir1.api-key`, `ceidg.api-key` (env-specific)                      |
| Invoice readiness         | All billing data stored, ready for VAT registration + KSeF               |

---

## 11. Prerequisites & API Key Status

| Prerequisite | Status | Notes |
|---|---|---|
| Consent Module (BE + FE) | **DONE** | `PROFESSIONAL_CHARACTER_DECLARATION` already in `LegalDocumentType` enum |
| GUS BIR 1.1 test key | **READY** | `abcde12345abcde12345` — dev can start immediately |
| GUS BIR 1.1 production key | **REQUESTED** | Email sent to `regon_bir@stat.gov.pl`, awaiting response |
| CEIDG production key | **READY** | Stored in `.env` as `CEIDG_APP_KEY` |
| Biała Lista | **READY** | No key needed (public API) |
| `PROFESSIONAL_CHARACTER_DECLARATION` PDFs | **TODO** | Need PL + EN PDFs in GCS + Liquibase seed |

---

# Appendix — As built: three-registry verification (2026-06)

The form expansion above shipped as part of a larger onboarding pipeline. Company
identity is verified against **three official Polish registries**, not self-declared:

1. The company enters its **NIP**; `NipValidator` normalizes and checksum-validates it
   (weighted mod-11) before anything leaves the building.
2. **GUS BIR 1.1** (`GusBir1RegistryAdapter`) is the mandatory identity source — a
   hand-rolled four-step SOAP session (login → search → full report + PKD report →
   guaranteed logout) returning legal form, names and address.
3. `CompanyTypeClassifier` maps the GUS legal-form code (JDG, sp. z o.o., S.A., …) and
   routes enrichment: **CEIDG** supplies the owner's name for sole proprietorships
   only; the **MF White List** adds VAT status and registered bank accounts in a
   deliberately degradation-tolerant mode — 404s, the 300/day quota and outages never
   block onboarding.
4. Lookup results are cached in-memory for 15 minutes so the **confirm step persists
   exactly what the user reviewed**, with raw-response evidence and per-source
   provenance flags stored in jsonb (`CompanyData`).
5. Confirmation can **auto-activate** the account when both email and registry data
   are verified — with Firebase role writes and the activation event deferred until
   after the PostgreSQL commit (the BUG-15 split-brain fix: the auth provider and the
   database can never disagree about account status).
6. NIP uniqueness is enforced twice: an early check for fast feedback, and a DB unique
   constraint that settles concurrent confirm races.

Key classes: `RegistryController` (STRICT rate limits) · `RegistryLookupService`
(orchestrator) · `GusBir1RegistryAdapter` · `CeidgRegistryAdapter` ·
`BialaListaVatAdapter` · `CompanyData` · `RegistryProperties`.
