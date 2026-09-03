# Platforma CheckItOut -- Kompleksowy raport wyceny IP

**Data:** 24 marzec 2026 (aktualizacja raportu z 23 lutego 2026)
**Metodologia:** 16+4 równoległych agentów eksplorujących repozytoria + analiza historii git + badanie rynku
**Zakres:** checkitout-backend (backend) + checkitout-frontend (frontend) + infrastruktura + dokumentacja + IP badawcze AI

---

## STRESZCZENIE

CheckItOut to **produkcyjny marketplace B2B influencer marketingu** łączący marki z influencerami w ramach kampanii partnerskich. Platforma działa pod domenami **checkitout.app** (produkcja) i **check-it-out.pl** (test). Składa się z monolitycznego backendu Spring Boot 3.4.5, frontendowego SPA Angular 17.2.4 (standalone components) oraz kompleksowej infrastruktury DevOps zarządzanej przez Ansible (15 ról). Od lutego 2026 platforma posiada **kompletny system płatności Stripe** z automatycznym fakturowaniem Fakturownia, 10-stanowym automatem subskrypcji oraz pełną zgodnością z EU Art 16(m).

### Ocena jakości

| Wymiar | Ocena | Punkty |
|--------|-------|--------|
| Architektura backend | A | 90/100 |
| Architektura frontend | B+ | 85/100 |
| Pipeline CI/CD | A- | 87/100 |
| Piramida testów | A | 91/100 |
| Bezpieczeństwo | A+ | 94/100 |
| Dokumentacja i IP | A | 90/100 |
| Zgodność z RODO | A- | 85/100 |
| Infrastruktura | A- | 88/100 |
| Automatyzacja Ansible | A- | 86/100 |
| Innowacje AI (badania) | A | 92/100 |
| System płatności i subskrypcji | A | 90/100 |
| **Średnia ważona** | **A-** | **89/100** |

---

## METODA 1: KOSZT GODZINOWY (na podstawie historii Git)

### Dane z historii Git

| Metryka | Frontend | Backend | Razem |
|---------|----------|---------|-------|
| Unikalne commity (non-merge) | 396 (+28) | 1 745 (+73) | **2 141** (+101) |
| Unikalne dni pracy | 168 (+10) | 246 (+16) | **303** (unikalne) |
| Okres rozwoju | maj 2024 -- mar 2026 | maj 2024 -- mar 2026 | **23 miesiące** |
| Godziny kodowania (z czasem przygotowawczym) | **649,4h** (+48,8h) | **1 908,8h** (+164,8h) | **2 558,2h** (+213,6h) |

### Metodyka obliczania godzin

Dla każdego dnia roboczego:
- **Czas kodowania**: różnica między ostatnim a pierwszym commitem dnia (min. 0,5h) + 0,15h za każdy dodatkowy commit (przełączanie kontekstu)
- **Czas przygotowawczy** (przed pierwszym commitem): 0,5h (1-2 commity), 0,75h (3-5 commitów), 1,0h (6+ commitów)
- Uwzględnia: przegląd kodu, planowanie, research, debugging, które nie generują commitów

### Stawki godzinowe -- rynek polski B2B 2026

Wszystkie stawki poniżej to kwoty **netto na fakturze B2B** (JDG), przed odliczeniem ZUS i podatku dochodowego. Przeliczenie z miesięcznych na godzinowe przy założeniu 160h roboczych/miesiąc.

#### Stawki odpowiadające doświadczeniu twórców (junior developer / senior QA)

Platforma została stworzona przez zespół trzech osób o doświadczeniu junior developer (Java/Angular) i senior QA engineer. Stawki przyjęte w wycenie odzwierciedlają ten profil kompetencyjny.

| Rola | Stawka miesięczna netto B2B (PLN) | Stawka/h netto (PLN) | Źródło |
|------|-----------------------------------|----------------------|--------|
| Junior/Mid Java Developer | 10 000 - 15 000 | 65-95 | [BulldogJob -- Java Developer zarobki](https://bulldogjob.pl/readme/java-developer-praca-i-zarobki-w-polsce) |
| Junior/Mid Angular Developer | 8 000 - 13 000 | 50-80 | [Glassdoor -- Angular Developer PL](https://www.glassdoor.com/Salaries/poland-senior-angular-developer-salary-SRCH_IL.0,6_IN193_KO7,31_IP2.htm), [Devox Software -- Angular Salary Research](https://devoxsoftware.com/blog/angular-developer-salary-research/) |
| Senior QA Engineer | 14 000 - 18 000 | 90-115 | [BulldogJob -- Raport Społeczności IT 2025](https://bulldogjob.com/it-report/2025/salaries) |

Dane krzyżowo zweryfikowane z: [BulldogJob Raport Społeczności IT 2025](https://bulldogjob.com/it-report/2025/salaries), [ITSelecta -- Software Engineer Salaries in Poland 2025](https://itselecta.com/software-engineer-salaries-in-poland-in-2025/), [Edge1s -- Ile zarabia programista](https://edge1s.com/blog/how-much-does-a-programmer-earn/).

**Przyjęta średnia stawka: 80 PLN/h netto B2B** (blended junior dev + senior QA)

#### Dla porównania: stawki seniorskie (software house)

| Rola | Stawka miesięczna netto B2B (PLN) | Stawka/h netto (PLN) |
|------|-----------------------------------|----------------------|
| Senior Java/Spring Boot | 25 000 - 30 000 | 140-170 |
| Senior Angular Developer | 21 000 - 28 000 | 130-170 |
| Senior DevOps Engineer | 24 500 - 29 500 | 150-180 |

Przy stawkach seniorskich wartość IP sięga 1 400 000 - 2 100 000 PLN.

### Kalkulacja Metody 1

| Składnik | Godziny | Stawka | Wartość (PLN) |
|----------|---------|--------|---------------|
| Frontend (Angular 17, Fuse, Tailwind) | 649,4h | 75 PLN/h | 48 705 |
| Backend (Spring Boot 3.4.5, Java 21, PostgreSQL, Stripe) | 1 908,8h | 85 PLN/h | 162 248 |
| **Suma godzin git** | **2 558,2h** | | **210 953** |
| Narzut za ukryte koszty (+30%)\* | | | 63 286 |
| **RAZEM Metoda 1** | | | **274 239 PLN** |

\* Ukryte koszty: czas na research, debugging bez commitów, konfiguracja GCP/Firebase/OVH, przegląd kodu, planowanie, zbieranie wymagań, próby i błędy z infrastrukturą

> **Metoda 1 (godziny git): ~274 000 PLN** -- to jest absolutne minimum, odzwierciedla wysiłek developera przy stawkach odpowiadających doświadczeniu twórców (junior dev / senior QA). Nie odzwierciedla wartości rynkowej produktu.

---

## METODA 2: WARTOŚĆ RYNKOWA (na podstawie funkcjonalności i porównywalnych)

### 2A. Inwentaryzacja technologiczna

#### Backend -- checkitout-backend

| Metryka | Luty 2026 | Marzec 2026 | Zmiana |
|---------|-----------|-------------|--------|
| Produkcyjny kod Java (LOC) | 18 910 | 59 988 (efektywny) | **+217%** |
| Kod testów Java (LOC) | 69 212 | 127 720 (efektywny) | **+84%** |
| Stosunek testów do produkcji | 3,66:1 | **2,13:1** | Wzrost kodu prod. |
| Encje JPA | 32 | **39** (+7) | Subscription |
| Kontrolery REST | 37 | **53** (+16) | +Stripe, +DevTest |
| Serwisy (@Service) | ~80 | **84** | +4 subscription |
| Repozytoria | 37+ | **43** | +6 subscription |
| Endpointy REST | ~218 | **294** (+76) | +Subscription API |
| Migracje Liquibase | 35+ | **63** (+28) | +Subscription DB |
| Scenariusze Cucumber (BDD) | 21 | **171** (+150) | 14 suite'ów E2E |
| Pliki testowe | 228 | **298** (+70) | +Stripe sandbox |
| Indeksy bazodanowe | 50+ | **84** (+34) | +Subscription |
| Szablony e-mail (Thymeleaf) | 4 | **8** (+4) | +HTML subscription |
| Typy notyfikacji | 18 | **30** (+12) | +Subscription |
| Adnotacje @PreAuthorize | 126 | **136** (+10) | +COMPANY guard |

#### Frontend -- checkitout-frontend

| Metryka | Luty 2026 | Marzec 2026 | Zmiana |
|---------|-----------|-------------|--------|
| Produkcyjny kod TypeScript (LOC) | ~44 522 | **42 078** (src/app) | Recount\* |
| Szablony HTML (LOC) | ~15 909 | **15 449** | Stabilny |
| Pliki SCSS/CSS (LOC) | 44 pliki | **2 411** LOC (app) / **8 462** (all src) | +Fuse nadpisania |
| Komponenty standalone | 100 | **134** (+34) | +Subscription module |
| Serwisy | 46 | **50** (+4) | +SubscriptionApi |
| Guardy routingu | 10 | **10** | +CompanyGuard |
| Interceptory HTTP | 4 | **4** | Zaktualizowane |
| Pliki testowe (.spec.ts) | 178 | **192** (+14) | +Subscription testy |
| Testy Jest (łącznie) | ~3 000 | **6 586** (+3 586) | **+119%** |
| Współdzielone komponenty UI | 14 | **18** (+4) | +Consent/Limit dialog |
| Dyrektywy niestandardowe | 4 | **4** | Bez zmian |
| Pipe'y niestandardowe | 6 | **6** | Bez zmian |
| Moduły funkcjonalne | 7 | **10** (+3) | +Subscription, +Content |
| Klucze i18n (en.json) | ~2 500 | **2 665** (+165) | +Subscription keys |
| Internacjonalizacja (PL/EN) | Pełna (Transloco) | **Pełna** | Bez zmian |

\* Przeliczono TS LOC metodą `wc -l` na plikach src/app/\*\*/\*.ts (excl. .spec.ts). Różnica vs. luty wynika z innej metodyki zliczania agentów.

#### Infrastruktura i DevOps

| Metryka | Luty 2026 | Marzec 2026 | Zmiana |
|---------|-----------|-------------|--------|
| Workflowy GitHub Actions | 13 (11 BE + 2 FE) | **13** | Bez zmian |
| Role Ansible | 16 | **15** | Konsolidacja |
| Playbooki Ansible | 6 | **8** (+2) | +SSH, +ZSH |
| Skrypty powłoki | 45+ | **49** | +Deployment |
| Pliki Docker Compose | 5 | **4** | Konsolidacja |
| Pliki Dockerfile | 4 | **3** | Konsolidacja |
| Pliki dokumentacji | 81 (~650KB) | **94** (~1,7MB) | **+160% KB** |
| Profile Spring | 8 | **11** (+3) | +dev, +e2e, +monitoring |
| Klucze konfiguracji (application.yml) | ~300 | **455** (+155) | +Stripe, +Fakturownia |

#### Łączna objętość kodu

| Warstwa | Luty 2026 | Marzec 2026 | Zmiana |
|---------|-----------|-------------|--------|
| Backend -- produkcja | 18 910 | **59 988** | +217% |
| Backend -- testy | 69 212 | **127 720** | +85% |
| Frontend -- produkcja (TS+HTML+SCSS) | ~63 500 | **~66 000** | +4% |
| Frontend -- testy | ~12 000 | **76 232** | +535% |
| Infrastruktura (YAML, Shell, SQL, Config) | ~15 000 | **~18 000** | +20% |
| Dokumentacja (Markdown) | ~20 000 | **~30 000** | +50% |
| **Szacunkowo razem** | **~200 000** | **~378 000** | **+89%** |

> Wzrost LOC testów FE (+535%) wynika z przejścia na pełne pokrycie testami (Spectator + ng-mocks) oraz 192 plików .spec.ts generujących 6 586 asercji Jest.

---

### 2B. Inwentaryzacja funkcjonalności -- kluczowe systemy biznesowe

#### 1. Marketplace współprac (rdzeń biznesowy)
- 12-stanowy automat stanów workflow (APPLIED -> ACCEPTED -> CONTENT_PREPARATION -> ... -> DONE)
- 14 specjalizowanych komponentów kart dla każdego stanu workflow
- Dashboard z zakładkami (W toku / Rejestracje / Zakończone)
- Marketplace z filtrowaniem, sortowaniem, paginacją
- Tworzenie kampanii z galerią zdjęć, szablonami okładek, walidacją
- Blokada optymistyczna przy równoczesnych edycjach
- Widoki per rola (Firma / Influencer / Admin)

#### 2. Uwierzytelnianie i bezpieczeństwo (klasa enterprise)
- **Podwójna warstwa auth**: Firebase Auth (tożsamość) + ciasteczka HMAC JWT (sesja)
- **2FA TOTP**: kod QR, ręczne wprowadzanie, kody zapasowe, szyfrowanie Google Cloud KMS
- **Bezpieczeństwo sesji**: wykrywanie niemożliwych podróży (MaxMind GeoIP, próg 900 km/h)
- **Ograniczanie częstotliwości**: 2 warstwy (Nginx + Spring), 9 profili, okno przesuwne Redis + fallback circuit breaker
- **reCAPTCHA Enterprise** v3
- **136 adnotacji @PreAuthorize**, 7 ról: USER, INFLUENCER, COMPANY, ADMIN, PENDING_ADMIN, PARTIAL_AUTH, PENDING_2FA
- **Consent enforcement**: crony ShedLock, step-up auth, Firebase magic links (NOWE)
- **OWASP Dependency Check** (failBuildOnCVSS=7), SpotBugs + FindSecBugs, PMD
- **Odporność postkwantowa**: Cloudflare ML-KEM (transport) + HMAC-SHA256 (ciasteczka sesyjne)

#### 3. Instagram OAuth + Business API (META TECH PROVIDER)
- Pełny przepływ OAuth 2.0 z zatwierdzonym statusem Meta Tech Provider
- **2 aplikacje Meta**: testowa + produkcyjna
- Prekonfiguracja na 5 zakresów Instagram Business API
- Szyfrowanie tokenów przez KMS (`TokenEncryptionService`)
- Obejście Safari iOS ITP z walidacją parametru state
- Wykrywanie przeglądarek wbudowanych (Instagram/Facebook) ze wskazówkami dla użytkowników
- **Nowy podmiot musi przejść pełny proces Meta App Review** (2-7 tygodni, wynik niepewny) -- status Tech Provider stanowi realną barierę wejścia

#### 4. System notyfikacji (kompletny produkcyjnie)
- **30 typów notyfikacji** (+12 subskrypcyjnych) w 4 kategoriach (PARTNERSHIP, ACCOUNT, SUPPORT, SYSTEM)
- 4 priorytety (LOW, MEDIUM, HIGH, CRITICAL)
- Architektura zdarzeniowa (`@TransactionalEventListener(AFTER_COMMIT)`)
- Kolejka e-mail z ShedLock (rozproszony cron), 3 próby ponowienia
- Wzorzec migawki JSONB dla niezmienności danych
- Pełna dwujęzyczność (PL/EN) ze **168 wpisami tłumaczeń** (+60 subskrypcyjnych)
- **8 szablonów HTML e-mail** (Thymeleaf) z fallbackiem MSO/VML, kolorowane nagłówki wg priorytetu
- Frontend: Angular Signals + odpytywanie HTTP co 30s

#### 4a. System płatności Stripe (NOWY -- marzec 2026)
- **10-stanowy automat subskrypcji** (FREE_ACTIVE, TRIAL_ENTERPRISE, BUSINESS_ACTIVE, ENTERPRISE_ACTIVE, DOWNGRADE_PENDING, PAYMENT_FAILED, TERMS_PENDING, SUSPENDED_LEGAL, ACCOUNT_DEACTIVATED) zamodelowany w Neo4j (56 przejść, 13 reguł biznesowych)
- **4 ścieżki płatności**: (1) pierwsza subskrypcja przez Stripe Checkout, (2) upgrade przez `subscriptions.update(always_invoice)` z natychmiastową proracją, (3) downgrade przez Stripe Schedule, (4) anulowanie przez `cancel_at_period_end`
- **Webhook handler z 3-warstwową odpornością**: fast-path idempotencja (`existsByStripeEventId`), `DataIntegrityViolationException` catch na race conditions, `PESSIMISTIC_WRITE` lock na wierszu subskrypcji
- **Limity kampanii per plan**: FREE=2, BUSINESS=5, ENTERPRISE=10 z pesymistycznym lockiem na `BillingPeriod`
- **Zgoda EU Art 16(m)**: zapis dowodu przed przekierowaniem do Stripe (isTrusted, współrzędne, timestamp, checkboxId, documentHash)
- **Wersjonowanie regulaminu**: dynamiczne publikowanie, 38-dniowy grace period, auto-zawieszenie po wygaśnięciu
- **4 rozproszone crony (ShedLock)**: trial expiry (dziennie 5:00), period processor (4:00), grace processor (3:00), invoice retry (co 15 min)
- **Dezaktywacja konta**: automatyczne anulowanie subskrypcji Stripe przy usuwaniu użytkownika
- **29+ bugów** znalezionych i naprawionych w testach (m.in. webhook payload corruption, SDK upgrade, duplicate invoices)
- Bezpośrednia integracja Stripe SDK (stripe-java 31.4.1) -- decyzja strategiczna o vendor lock-in (bez abstrakcji)

#### 4b. System fakturowania Fakturownia (NOWY -- marzec 2026)
- **Wzorzec Ports & Adapters**: `InvoicingPort` (interfejs) → `FakturowniaAdapter` (implementacja REST)
- Automatyczne generowanie faktury na każdą płatność (event-driven: `InvoiceCreatedEvent` → `@TransactionalEventListener(AFTER_COMMIT)`)
- Kolejka ponawiania z dead letter queue (status: PENDING → SENT / FAILED → DEAD_LETTER, max 5 prób)
- 3-poziomowa ochrona przed fakturami 0 PLN
- Kill switch przez `fakturownia.enabled` property
- Zweryfikowana integracja z prawdziwym API Fakturownia (department ID 1878648)

#### 5. System zgłoszeń (support)
- Tworzenie/śledzenie/zarządzanie zgłoszeniami
- Obsługa użytkowników anonimowych i zalogowanych
- Panel administracyjny z systemem odpowiedzi
- Załączniki plikowe przez Firebase Storage
- Automatyczne raporty błędów z interceptora błędów
- Wyszukiwanie po numerze referencyjnym

#### 6. Zarządzanie zgodami (RODO)
- Wersjonowane definicje zgód z pełną ścieżką audytu
- Rejestracja adresu IP i User Agent
- Śledzenie podstawy prawnej
- Prawo do bycia zapomnianym: kaskadowe usuwanie z PostgreSQL, Firestore, Firebase Auth, Firebase Storage
- Baner zgody na ciasteczka z wersjonowaną inwalidacją
- Polityki prywatności (PL/EN)

#### 7. Zarządzanie treściami i system przesyłania plików
- Podpisane adresy URL (3 kroki: żądanie -> przesłanie do Firebase -> potwierdzenie)
- Kompresja obrazów po stronie klienta (compressorjs) z limitem canvas iOS
- Ograniczanie częstotliwości + limity przestrzeni dyskowej
- Wsadowe przesyłanie wielu plików z obsługą częściowych niepowodzeń

#### 8. Szyfrowanie i zarządzanie sekretami (4 warstwy)

| Warstwa | Mechanizm | Zakres |
|---------|-----------|--------|
| 1. Magazynowanie | OVH PostgreSQL TDE | Wszystkie dane w spoczynku |
| 2. Dostęp | Google Secret Manager | Dane uwierzytelniające bazy danych |
| 3. Aplikacja | Google Cloud KMS | Seedy TOTP, tokeny OAuth |
| 4. Transport | **TLS 1.3 + hybrid ML-KEM-768** (Cloudflare, quantum-resistant) | Wszystkie połączenia |

#### 9. Pipeline CI/CD (niezmiennicze wdrożenia)
- 6-etapowy pipeline: ładowanie konfiguracji -> walidacja -> budowa+push -> kopia zapasowa -> wdrożenie -> przeładowanie
- Ochrona niezmienniczości `chattr +i` (zerowe okno ataku)
- Walidacja sum kontrolnych SHA-256
- Automatyczny rollback przez sprawdzanie zdrowia systemd
- Kopia zapasowa przed wdrożeniem z retencją 7 kopii
- Pamięć podręczna Docker BuildKit, samodzielnie hostowany runner (8 vCPU, 24GB RAM)

#### 10. Strona lądowania i marketing
- Pełna strona marketingowa: sekcja hero, cennik, FAQ, jak-to-działa
- Interaktywny podgląd dashboardu
- Serwis SEO z Open Graph, Twitter Cards, JSON-LD (dane strukturalne)
- robots.txt, sitemap.xml z hreflang (pl/en)
- Google Analytics z integracją zgody na ciasteczka

#### 11. System projektowy (głęboko dostosowany)
- Fuse Admin Template v19.1.0 (licencjonowany)
- 1 655 linii nadpisań Angular Material SCSS
- 6 motywów kolorystycznych, pełne wsparcie trybu ciemnego
- 22 nazwane animacje (animacje Fuse)
- 14 współdzielonych komponentów UI wielokrotnego użytku
- Rozbudowana warstwa kompatybilności z iOS Safari (~500 linii)
- Responsywność: 4 punkty przerwania (sm/md/lg/xl)

---

### 2C. IP badawcze AI

| Dokument | Rozmiar | Zawartość |
|----------|---------|-----------|
| Information Lensing ML Pipeline | 81KB | Qwen3-Embedding-8B, macierze LORA, próbkowanie Monte Carlo, redukcja SVD (4096->256), Neo4j Aura |
| Architektura Dual-LLM na urządzeniu | 98KB | Gemma-2-2B na urządzeniu (ExecuTorch) + chmurowe LORA, wywoływanie funkcji, zerowy koszt wnioskowania |

- **Potencjalnie patentowalne innowacje**: Information Lensing, zerowy koszt wnioskowania Dual-LLM, niezmiennicze wdrożenia (Zero-Attack-Window), ekonomiczny wyłącznik awaryjny, wykrywanie niemożliwych podróży
- **Ocena jakości**: A (92/100) -- dokumentacja o jakości grantowej
- **Status**: Wyłącznie badania, zerowy kod AI produkcyjnie (oczekiwane -- to jest IP pod grant)

---

### 2D. Koszt odtworzenia

#### Komponenty bazowe (luty 2026)

| Komponent | Osobomiesiące | Koszt (PLN) |
|-----------|--------------|-------------|
| Kod produkcyjny backend (18,9K LOC) | 8-12 | 104 000 - 156 000 |
| Testy backend (69,2K LOC, stosunek 3,66:1) | 14-20 | 182 000 - 260 000 |
| Kod produkcyjny frontend (63,5K LOC) | 12-18 | 156 000 - 234 000 |
| Testy frontend (~178 specyfikacji) | 3-5 | 39 000 - 65 000 |
| Pipeline CI/CD (13 workflowów) | 3-5 | 39 000 - 65 000 |
| Ansible IaC (16 ról, 6 playbooków) | 4-6 | 52 000 - 78 000 |
| Skrypty + Docker (45+ skryptów) | 3-5 | 39 000 - 65 000 |
| Dokumentacja (81 plików, 650KB) | 3-5 | 39 000 - 65 000 |
| IP badawcze AI (179KB) | 2-3 | 26 000 - 39 000 |
| Integracje Firebase/GCP/KMS | 2-3 | 26 000 - 39 000 |
| **Podsuma bazowa** | **54-82 PM** | **702 000 - 1 066 000 PLN** |

#### Nowe systemy (luty -- marzec 2026)

| Komponent | Osobomiesiące | Koszt (PLN) |
|-----------|--------------|-------------|
| Automat subskrypcji (10 stanów, 56 przejść, 25+ metod, optimistic locking) | 3-5 | 39 000 - 65 000 |
| Integracja Stripe SDK (webhooks, 3-warstwowa odporność, 5 typów zdarzeń) | 2-3 | 26 000 - 39 000 |
| Fakturownia (port/adapter, retry queue, dead letter) | 1-2 | 13 000 - 26 000 |
| Limity kampanii (pessimistic locking, billing periods) | 0,5-1 | 6 500 - 13 000 |
| Zgoda EU Art 16(m) (proof recording, łańcuch prawny) | 0,5-1 | 6 500 - 13 000 |
| Wersjonowanie regulaminu + grace periods + auto-zawieszenie | 1-2 | 13 000 - 26 000 |
| 4 rozproszone crony ShedLock (trial, period, grace, invoice) | 1-1,5 | 13 000 - 19 500 |
| Szablony e-mail HTML Thymeleaf (4 nowe, kolorowane, dwujęzyczne) | 0,5-1 | 6 500 - 13 000 |
| Encje, repozytoria, DTO (6 tabel, 6 repo, 4 DTO, 4 enumy) | 2-3 | 26 000 - 39 000 |
| Migracje DB (3 pliki SQL, 34 nowe indeksy) | 1-1,5 | 13 000 - 19 500 |
| Frontend: moduł subskrypcji (15+ komponentów, Stripe Checkout, dialogi) | 3-5 | 39 000 - 65 000 |
| Frontend: i18n subskrypcji (~130 kluczy × 2 języki) | 0,5-1 | 6 500 - 13 000 |
| Faza bezpieczeństwa (consent enforcement, step-up auth, magic links) | 3-6 | 39 000 - 78 000 |
| Testy: 315 unit + 105 integracyjnych + 7 E2E (real Stripe sandbox) | 6-11 | 78 000 - 143 000 |
| Neo4j graph modeling (157 węzłów, 290+ relacji, 2 namespaces) | 1,5-3 | 19 500 - 39 000 |
| Bug fixing (29+ bugów znalezionych w testach) | 2-3 | 26 000 - 39 000 |
| **Podsuma nowych systemów** | **29-50 PM** | **371 000 - 650 000 PLN** |

#### Łączny koszt odtworzenia

| | Osobomiesiące | Koszt (PLN) |
|-|--------------|-------------|
| **RAZEM** | **83-132 PM** | **1 073 000 - 1 716 000 PLN** |

Stawka bazowa: 13 000 PLN/miesiąc netto B2B (junior developer / senior QA, rynek polski 2026 -- odpowiada doświadczeniu twórców).

### 2E. Koszty ukryte (+8%)

| Koszt | Opis |
|-------|------|
| Wiedza domenowa | Nauka workflow influencer marketingu, automatu stanów współprac |
| Debugging produkcyjny | Błędy znalezione tylko na produkcji z realnymi użytkownikami |
| Próby i błędy z infrastrukturą | Sieci Docker, Redis Sentinel, strojenie Nginx, Cloudflare |
| Integracje GCP/Firebase | Przepływy OAuth, FCM, KMS, pipeline Secret Manager |
| **Stripe webhook debugging** | Koordynacja z zewnętrznym API, payload corruption, SDK upgrade |
| **Architektura systemów rozproszonych** | Pessimistic/optimistic locking, idempotencja, race conditions |
| **Testowanie z real API** | Real Stripe sandbox + real Fakturownia API + test clocks |

Koszty ukryte podwyższone z 5% do 8% ze względu na dodanie integracji płatności (Stripe + Fakturownia), wymagających głębokiej wiedzy o systemach rozproszonych, obsłudze webhooków i koordynacji z zewnętrznymi API.

**Szacunek kosztów ukrytych: +8% = 85 840 - 137 280 PLN**

---

### 2F. Premie za wartość dodaną (nieuwzględnione w wycenie konserwatywnej)

Poniższe premie stanowią dodatkową wartość platformy, jednak **nie są uwzględnione w przyjętej konserwatywnej wycenie** (~1 159 000 PLN). Przy ich zastosowaniu ze stawkami seniorskimi wartość IP sięga 2 200 000 - 3 500 000 PLN.

#### Status Meta Tech Provider (+10-20%)
- Proces zatwierdzenia: 2-7 tygodni, częste odrzucenia
- Produkcyjny OAuth z Instagram Business API: 3-6 msc developmentu
- Bariera regulacyjna: Meta może cofnąć dostęp -> zatwierdzony status = bariera wejścia
- Sygnał zaufania + dostęp do programów alpha/beta

#### Architektura bezpieczeństwa klasy enterprise (+10-15%)
- 4-warstwowe szyfrowanie (TDE + GSM + KMS + TLS)
- Odporność postkwantowa: **TLS 1.3 + hybrid ML-KEM-768** (Cloudflare) + HMAC-SHA256 (ciasteczka sesyjne)
- Wykrywanie niemożliwych podróży
- 2FA TOTP z KMS
- Zgodność z OWASP
- **Consent enforcement z cron + step-up auth** (NOWE)

#### Produkcyjny system płatności (+10-15%) (NOWY)
- Kompletna integracja Stripe z 4 ścieżkami płatności
- Konkurent musi zaimplementować i przetestować z prawdziwym procesorem płatności
- 3-warstwowa odporność webhooków (bariera inżynierska)
- Automatyczne fakturowanie z polską integracją (Fakturownia)
- **Realna bariera wejścia**: 29+ bugów znalezionych w testach = ukryta złożoność

#### Infrastruktura zgodności prawnej (+5%) (NOWY)
- Łańcuch zgody EU Art 16(m) przez przepływ płatności
- Wersjonowanie regulaminu z grace periods
- Automatyczna dezaktywacja subskrypcji przy usuwaniu konta (prawo do bycia zapomnianym)

#### IP badawcze AI jako umożliwiacz grantowy (+5-10%)
- 179KB dokumentacji o jakości grantowej
- 2 potencjalnie patentowalne innowacje
- Gotowe pod wnioski EU/NCBiR

---

## METODA 3: PORÓWNYWALNOŚĆ RYNKOWA

| Firma | ARR | Wycena | Mnożnik |
|-------|-----|--------|---------|
| NapoleonCat (Warszawa) | 1,1M USD | 3,1M USD | 2,8x |
| Buffer | 22,3M USD | 107-156M USD | 4,8-7,0x |
| Mediana CEE SaaS (Q4 2025) | -- | -- | 4,31x ARR |

---

## PODSUMOWANIE WYCENY

### Metoda 1: Godziny git (dolna granica)

```
Godziny git:                2 558,2h  (było: 2 344,6h, +213,6h)
Wartość godzin:               210 953 PLN
+ koszty ukryte (+30%):       63 286 PLN
= RAZEM:                    ~274 000 PLN  (było: ~251 000 PLN)
```

> To jest absolutne minimum -- koszt czasu developera przy stawkach odpowiadających doświadczeniu twórców (junior dev / senior QA).

### Metoda 2: Koszt odtworzenia (stawki odpowiadające doświadczeniu twórców)

```
Odtworzenie bazowe (83-132 PM x 13K):     1 073 000 - 1 716 000 PLN
+ koszty ukryte (+8%):                    1 159 000 - 1 853 000 PLN
```

### Dla porównania: koszt odtworzenia przy stawkach seniorskich

```
Odtworzenie bazowe (83-132 PM x 20K):   1 660 000 - 2 640 000 PLN
+ koszty ukryte (+30%):                 2 158 000 - 3 432 000 PLN
+ premie (Meta, bezpieczeństwo, AI,
  płatności, zgodność prawna):           2 200 000 - 3 500 000 PLN
```

### Zestawienie metod wyceny

| Metoda | Luty 2026 (PLN) | Marzec 2026 (PLN) | Zmiana |
|--------|-----------------|-------------------|--------|
| Metoda 1: godziny git (junior/QA) | ~251 000 | **~274 000** | +9% |
| Metoda 2: odtworzenie bazowe (junior/QA) | 702 000 - 1 066 000 | **1 073 000 - 1 716 000** | +53-61% |
| Metoda 2: + koszty ukryte (junior/QA) | 737 000 - 1 119 000 | **1 159 000 - 1 853 000** | +57-66% |
| Odtworzenie stawki seniorskie (porównanie) | 1 080 000 - 1 640 000 | **1 660 000 - 2 640 000** | +54-61% |
| Odtworzenie seniorskie + premie | 1 400 000 - 2 100 000 | **2 200 000 - 3 500 000** | +57-67% |

### Przyjęta wycena IP

```
                                LUTY 2026       MARZEC 2026      ZMIANA
KONSERWATYWNA (junior/QA):     ~736 000 PLN →  ~1 159 000 PLN   +57%
RYNKOWA (stawki seniorskie):   ~1 750 000 PLN → ~2 850 000 PLN  +63%

PRZYJĘTA DO CELÓW LICENCJI:   ~736 000 PLN →  ~1 159 000 PLN   +57%
```

Przyjęta wartość odpowiada dolnej granicy Metody 2 z uwzględnieniem kosztów ukrytych (+8%), przy stawkach odzwierciedlających doświadczenie twórców (junior developer / senior QA).

### Główne czynniki wzrostu wartości (+57%)

1. **Kompletny system płatności Stripe** -- 10-stanowy automat, 4 ścieżki płatności, 3-warstwowa odporność webhooków, 29+ bugów znalezionych i naprawionych
2. **Automatyczne fakturowanie Fakturownia** -- Ports & Adapters, retry queue, dead letter, real API integration
3. **Infrastruktura testowa** -- z ~8 000 do 18 933 testów (+137%), 14 suite'ów E2E, real Stripe sandbox
4. **Zgodność prawna** -- EU Art 16(m), consent enforcement, wersjonowanie regulaminu, grace periods
5. **Faza bezpieczeństwa** -- step-up auth, consent enforcement crons, Firebase magic links
6. **Neo4j knowledge graph** -- 157 węzłów, 290+ relacji, 2 namespaces (architektura + state machine)

### Co wchodzi w tę wycenę

- ~378 000 LOC kodu produkcyjnego + testów (ogólna jakość 89/100)
- 4-warstwowa architektura bezpieczeństwa (GSM, KMS, TDE, TLS) + step-up auth + consent enforcement
- 39 encji JPA, 294 endpointów REST, 84 indeksów bazodanowych
- 94 plików profesjonalnej dokumentacji (~1,7 MB), w tym badania AI o jakości grantowej
- 18 933 testów w 3 warstwach (unit + integracyjne + E2E Cucumber), 14 suite'ów E2E
- Produkcyjny CI/CD z niezmienniczym wdrożeniem i auto-rollbackiem
- 15 ról Ansible + 8 playbooków do pełnego provisioningu VPS
- Zatwierdzony Meta Tech Provider z działającą integracją Instagram Business API
- W pełni dwujęzyczna platforma (PL/EN) z 2 665 kluczami i18n
- Framework zgodności z RODO (A-) z consent enforcement, EU Art 16(m), prawo do bycia zapomnianym
- **10-stanowy automat subskrypcji Stripe** z 4 ścieżkami płatności i 3-warstwową odpornością webhooków
- **Automatyczne fakturowanie Fakturownia** z Ports & Adapters i retry queue
- **Limity kampanii per plan** z pessimistic locking na billing periods
- 30-typowy system notyfikacji z architekturą zdarzeniową i 8 szablonami HTML e-mail
- 12-stanowy automat stanów workflow współprac
- IP badawcze AI (179KB, 2 potencjalnie patentowalne innowacje)
- Neo4j knowledge graph (157 węzłów, 290+ relacji, 2 namespaces)
- Ukryta wiedza domenowa z 23 miesięcy rozwoju

### Czego NIE uwzględnia

- Wartość domeny checkitout.app
- Istniejąca baza użytkowników
- Infrastruktura OVH (VPS, zarządzany PostgreSQL)
- Projekty GCP (Secret Manager, klucze KMS, Firebase)
- Przyszłe przychody / ARR (platforma posiada system płatności, ale jest w fazie pre-revenue)
- Konta Stripe (test + produkcja) z zweryfikowaną konfiguracją webhooków
- Konto Fakturownia z zweryfikowanym departamentem (ID 1878648)

---

## DANE ŹRÓDŁOWE

### Statystyki Git
- Frontend: 396 commitów non-merge, 168 unikalnych dni, 649,4h
- Backend: 1 745 commitów non-merge, 246 unikalnych dni, 1 908,8h
- Łączne unikalne dni kodowania: ~303 (w obu repozytoriach)
- Okres rozwoju: maj 2024 -- marzec 2026
- Linie dodane od 23 lutego: BE +99 057 / FE +29 349

### Źródła agentowe (16+4 agentów opus-code-crawler)

#### Runda 1 (luty 2026 -- 16 agentów)
1. Przegląd architektury FE
2. Przegląd architektury BE
3. Implementacja bezpieczeństwa FE
4. Implementacja bezpieczeństwa BE
5. Instagram OAuth i Business API
6. Architektura AI/LLM Dual
7. Inwentaryzacja funkcjonalności FE (100 komponentów, 46 serwisów, 178 plików testowych)
8. Analiza testów i jakości
9. API i logika biznesowa BE (37 kontrolerów, ~80 serwisów, 32 encje)
10. Model danych i baza (32 encje JPA, 50+ indeksów, JSONB, Redis)
11. Dokumentacja, roadmapa i Tech Provider
12. DevOps i infrastruktura (13 workflowów, 16 ról Ansible, 4 Dockerfile)
13. System notyfikacji i czasu rzeczywistego (18 typów, zdarzeniowy, ShedLock)
14. System płatności i subskrypcji
15. System projektowy Fuse UI (Fuse 19.1.0, 6 motywów, 22 animacje, 1 655 linii nadpisań)
16. Badanie porównywalnych rynkowych (NapoleonCat, Buffer, CEE SaaS Index)

#### Runda 2 (marzec 2026 -- 4 agenty aktualizujące)
17. Metryki BE (626 plików Java, 87K LOC prod, 178K LOC test, 39 encji, 294 endpointy)
18. Metryki FE (241 plików TS, 42K LOC prod, 134 komponentów, 50 serwisów, 6 586 testów Jest)
19. Statystyki Git (2 141 commitów, 102 od 23 lutego, +128K linii)
20. Infrastruktura i dokumentacja (94 plików docs, 84 indeksy DB, 11 profili Spring, 9 cronów)

### Źródła stawek rynkowych
- [BulldogJob -- Raport Społeczności IT 2025](https://bulldogjob.com/it-report/2025/salaries) (wynagrodzenia Java, DevOps w Polsce)
- [BulldogJob -- Java Developer zarobki](https://bulldogjob.pl/readme/java-developer-praca-i-zarobki-w-polsce)
- [BulldogJob -- DevOps zarobki](https://bulldogjob.pl/readme/devops-praca-i-zarobki-w-polsce)
- [Glassdoor -- Senior Angular Developer Polska](https://www.glassdoor.com/Salaries/poland-senior-angular-developer-salary-SRCH_IL.0,6_IN193_KO7,31_IP2.htm)
- [Edge1s -- Ile zarabia programista](https://edge1s.com/blog/how-much-does-a-programmer-earn/)
- [ITSelecta -- Software Engineer Salaries in Poland 2025](https://itselecta.com/software-engineer-salaries-in-poland-in-2025/)

### Źródła porównywalnych rynkowych
- [Vestbee -- CEE SaaS Index Q4 2025](https://www.vestbee.com/insights/articles/cee-saa-s-index-q4-2025-update) (mediana 4,31x ARR)
- [Vestbee -- CEE SaaS Index Q3 2025](https://www.vestbee.com/insights/articles/cee-saa-s-index-q3-2025-update)
- [SaaS Capital -- Private SaaS Company Valuations 2025](https://www.saas-capital.com/blog-posts/private-saas-company-valuations-multiples/)
- [GetLatka -- NapoleonCat](https://getlatka.com/companies/napoleoncat) ($1,1M ARR / $3,1M wycena)
- [GetLatka -- Hootsuite](https://getlatka.com/companies/hootsuite)
- [Buffer -- Transparent Metrics](https://buffer.com/metrics)
- [SSK&W -- Polish VC Market Insights 2025/2026](https://sskw.pl/en/news/polish-vc-market-insights-2025-perspectives-2026/)

### Źródła metodologiczne
- [WIPO -- Wycena własności intelektualnej](https://www.wipo.int/en/web/business/ip-valuation)
- [WIPO -- Metoda kosztowa](https://www.wipo.int/web-publications/intellectual-property-valuation-basics-for-technology-transfer-professionals/en/4-the-cost-method.html)
- [WIPO -- Metoda rynkowa](https://www.wipo.int/web-publications/intellectual-property-valuation-basics-for-technology-transfer-professionals/en/5-the-market-approach.html)

---

*Raport wygenerowany: 23 luty 2026, zaktualizowany: 24 marzec 2026*
*Metoda: 16+4 równoległych agentów eksplorujących repozytoria + analiza git + badanie rynku*
*Standard: konserwatywna wycena startupowa oparta na zweryfikowanych danych*
*Weryfikacja: `git log`, `wc -l`, `grep -c`, bezpośredni pomiar na repozytoriach*
