# Uzasadnienie wyceny licencji IP -- Platforma CheckItOut

**Data:** 24 marzec 2026 (aktualizacja dokumentu z 23 lutego 2026)
**Dotyczy:** Licencja niewyłączna na oprogramowanie CheckItOut (frontend + backend + infrastruktura)

---

## CEL DOKUMENTU

Niniejszy dokument stanowi uzasadnienie **godziwej wartości rynkowej licencji** na oprogramowanie platformy CheckItOut, udzielonej spółce przez jej twórców (członków zarządu będących jednocześnie wspólnikami).

Wycena jest sporządzona na potrzeby **ujęcia licencji jako wartości niematerialnej i prawnej (WNiP)** w ewidencji księgowej spółki, zgodnie z art. 3 ust. 1 pkt 14 ustawy o rachunkowości. Licencja na autorskie prawa majątkowe do oprogramowania stanowi aktywo niematerialne podlegające ewidencji w bilansie spółki.

**Ustalona godziwa wartość licencji (~290 000 PLN)** stanowi wartość początkową WNiP do ujęcia w księgach rachunkowych.

---

## 1. PRZEDMIOT LICENCJI

Oprogramowanie platformy CheckItOut -- marketplace B2B łączący marki z influencerami w ramach kampanii marketingowych. Platforma składa się z:

- **Backend**: Spring Boot 3.4.5, Java 21, PostgreSQL 17, Redis 7, **Stripe 31.4.1**, **Fakturownia**
- **Frontend**: Angular 17.2.4, Tailwind CSS 3.4.1, Angular Material 17.2.2, **Stripe.js**
- **Infrastruktura**: 13 workflowów CI/CD (GitHub Actions), 15 ról Ansible, Docker (wieloetapowy)
- **Dokumentacja**: 94 plików (~1,7MB), w tym dokumentacja AI/ML o jakości grantowej

Platforma działa produkcyjnie pod domenami checkitout.app (produkcja) i check-it-out.pl (test).

Szczegółowa inwentaryzacja technologiczna, metryki kodu i pełna analiza funkcjonalności znajdują się w załączonym raporcie: **ip-valuation-comprehensive-feb-2026.md**.

---

## 2. CAŁKOWITA WARTOŚĆ IP

### 2.1. Kluczowe metryki

| Metryka | Wartość |
|---------|---------|
| Łączna liczba commitów (non-merge) | **2 141** |
| Łączna liczba unikalnych dni pracy | **~303** |
| Łączna liczba godzin kodowania (z czasem przygotowawczym) | **2 558,2h** |
| Okres rozwoju | maj 2024 -- marzec 2026 (**23 miesiące**) |
| Łączny LOC (produkcja + testy + infra + dokumentacja) | **~378 000** |
| Łączna liczba testów (unit + integracyjne + E2E) | **18 933** |
| Ocena jakości ogólnej | **A- (89/100)** |

### 2.2. Wycena metodą kosztu odtworzenia

Na podstawie analizy 16 równoległych agentów eksplorujących oba repozytoria kodu, weryfikacji historii git oraz porównania z benchmarkami rynkowymi, koszt odtworzenia platformy od zera przy zatrudnieniu developerów o doświadczeniu odpowiadającym twórcom (junior developer / senior QA, stawka ~13 000 PLN/miesiąc netto na fakturze B2B -- wg [BulldogJob IT Report 2025](https://bulldogjob.com/it-report/2025/salaries), [Edge1s -- Ile zarabia programista](https://edge1s.com/blog/how-much-does-a-programmer-earn/)) wynosi:

| | Osobomiesiące | Koszt (PLN) |
|--|--------------|-------------|
| Odtworzenie bazowe | 83-132 | 1 073 000 - 1 716 000 |
| + koszty ukryte (+8%) | | 1 159 000 - 1 853 000 |

Koszty ukryte podwyższone z 5% do 8% ze względu na dodanie integracji płatności (Stripe + Fakturownia), wymagających wiedzy o systemach rozproszonych i koordynacji z zewnętrznymi API. Nadal poniżej standardu branżowego (25-30%).

Pełna tabela kosztów per komponent -- patrz raport techniczny, sekcja 2D.

### 2.3. Profil doświadczenia twórców

Platforma została stworzona przez zespół trzech osób, których doświadczenie w momencie rozpoczęcia prac (maj 2024) odpowiadało poziomowi **junior developer** (Java/Angular) oraz **senior QA engineer**. Stawki przyjęte w wycenie odzwierciedlają ten poziom kompetencji, a nie stawki seniorów, co obniża bazową wartość IP.

### 2.4. Przyjęta pełna wartość IP

**~1 159 000 PLN** (dolny zakres kosztu odtworzenia z uwzględnieniem kosztów ukrytych, przy stawkach odpowiadających doświadczeniu twórców).

Przy zastosowaniu stawek seniorskich (20 000 PLN/miesiąc) wartość IP sięga 2 200 000 - 3 500 000 PLN (patrz raport techniczny, sekcja Podsumowanie wyceny).

---

## 3. WARTOŚĆ LICENCJI

### 3.1. Charakter licencji

| Parametr | Wartość |
|----------|---------|
| Typ | **Niewyłączna** (non-exclusive) |
| Stan | **As-is** -- bez gwarancji, bez SLA |
| Wsparcie | **Brak** -- po ewentualnym odejściu twórców z zarządu brak obowiązku dalszego wsparcia i rozwoju |
| Czas trwania | Bezterminowa |

### 3.2. Dyskonto za charakter licencji

Licencja niewyłączna, bez wsparcia, w stanie „as-is" ma istotnie niższą wartość rynkową niż pełne przeniesienie praw autorskich z gwarancją i SLA.

#### Czynnik 1: Licencja niewyłączna (-50%)

| Czynnik dyskonta | Uzasadnienie | Wpływ |
|-------------------|--------------|-------|
| **Licencja niewyłączna** | Licencjodawcy zachowują prawa do kodu; mogą licencjonować go innym podmiotom; licencjobiorca nie ma wyłączności | **-50%** |

Uzasadnienie poziomu dyskonta:

- Wg [WIPO](https://www.wipo.int/en/web/business/ip-valuation), zakres udzielonych praw stanowi kluczowy czynnik wpływający na wartość licencji -- licencja wyłączna, obejmująca wszystkie pola eksploatacji, jest z natury znacznie cenniejsza niż licencja niewyłączna.
- Badanie [LES (Licensing Executives Society) High Tech Royalty Survey 2021](https://ipwatchdog.com/2022/10/03/les-2021-royalty-survey-reports-licensing-market-update-look-back-les-royalty-valuation-method-making/) -- jedyne źródło branżowe kwantyfikujące premię za wyłączność (*exclusivity premium*) -- potwierdza, że licencje wyłączne w sektorze technologicznym osiągają istotnie wyższe stawki royalty niż niewyłączne. Wśród 155 przeanalizowanych transakcji, 48% stanowiły licencje wyłączne, a 45% niewyłączne, przy czym wyłączne uzyskiwały systematycznie wyższą wycenę.
- Czynnik 4. [Georgia-Pacific](https://www.ipglossary.com/glossary/georgia-pacific-factors/) (standard sądowy USA stosowany również w praktyce europejskiej) wprost wskazuje charakter licencji (wyłączna vs. niewyłączna) jako jeden z 15 czynników determinujących godziwą stawkę royalty.
- W praktyce rynkowej licencje niewyłączne na oprogramowanie wyceniane są na poziomie 25-50% wartości licencji wyłącznej ([PatentPC -- IP Valuation on Licensing](https://patentpc.com/blog/the-impact-of-ip-valuation-on-licensing-deal-terms-and-royalties), [FasterCapital -- License Valuation](https://fastercapital.com/content/License-valuation--How-to-Value-Your-License-and-Maximize-Your-Intellectual-Property-Value.html)). Przyjęte 50% (górna granica zakresu) odzwierciedla fakt, że platforma jest produktem niszowym z ograniczoną liczbą potencjalnych licencjobiorców.

#### Czynnik 2: Brak wsparcia / stan „as-is" (-25%)

| Czynnik dyskonta | Uzasadnienie | Wpływ |
|-------------------|--------------|-------|
| **Brak wsparcia (as-is)** | Brak gwarancji na działanie, brak obowiązku naprawiania błędów, brak aktualizacji, brak SLA; po odejściu twórców z zarządu spółka korzysta z kodu w stanie zastanym | **-25%** |

Uzasadnienie poziomu dyskonta:

- Standardowa roczna opłata za utrzymanie i wsparcie oprogramowania to **15-25% wartości licencji rocznie** ([Brainsell -- Maintenance Fees](https://www.brainsell.com/blog/maintenance-fees-what-are-you-actually-paying-for/), [SOLTECH -- Software Maintenance Costs](https://soltech.net/software-support-and-maintenance-costs/), [Avasant -- Software Maintenance Fees](https://avasant.com/report/high-software-maintenance-fees-and-what-to-do-about-them/)). Typowa stawka to ok. 20% rocznie ([SoftwareOne](https://www.softwareone.com/en/blog/articles/2020/11/30/how-can-you-save-costs-on-support-and-maintenance)).
- Przy braku wsparcia licencjobiorca traci: naprawianie błędów, aktualizacje bezpieczeństwa, aktualizacje zależności, SLA na czas reakcji i dostępność.
- Przyjęte 25% dyskonto odpowiada skapitalizowanej wartości ~1,25 roku brakującego wsparcia (1,25 × 20% = 25%), co jest zgodne z górną granicą standardu branżowego.

#### Łączne dyskonto

| | Wpływ |
|--|-------|
| Licencja niewyłączna | -50% |
| Brak wsparcia (as-is) | -25% |
| **Łączne dyskonto** | **-75%** |

### 3.3. Godziwa wartość rynkowa licencji

```
Pełna wartość IP:                    1 159 000 PLN
Dyskonto za licencję niewyłączną:       -50%  =  -579 500 PLN
Dyskonto za as-is / brak wsparcia:      -25%  =  -289 750 PLN
Łączne dyskonto:                        -75%  =  -869 250 PLN
                                         ─────────────────────
Godziwa wartość licencji:             ~290 000 PLN
```

**Godziwa wartość rynkowa licencji wynosi ~290 000 PLN.** Wartość ta stanowi podstawę do oceny adekwatności wynagrodzenia uzgodnionego w umowie licencyjnej (sekcja 4).

---

## 4. WYNAGRODZENIE ZA LICENCJĘ

W zamian za udzielenie licencji twórcy otrzymują **warunkowe prawo do udziału w zysku spółki** -- jednorazową premię powiązaną z osiągnięciem kamienia milowego rentowności. Jest to jedyne wynagrodzenie za licencję; nie przewiduje się jednorazowej opłaty pieniężnej.

### 4.1. Warunki wynagrodzenia

| Parametr | Wartość |
|----------|---------|
| Forma | **Warunkowe prawo do zysku** (jednorazowa premia) |
| Warunek | Zysk netto spółki przekracza **1 000 000 PLN** w pierwszym roku obrotowym |
| Charakter | **Jednorazowa** -- wypłacana raz po spełnieniu warunku |
| Łączna wysokość | **7% zysku netto** za rok obrotowy, w którym warunek został spełniony |

### 4.2. Podział wynagrodzenia

| Osoba | Udział | Przy zysku 1M PLN |
|-------|--------|-------------------|
| Norbert Marchewka | 3,50% | 35 000 PLN |
| Piotr Żmudzki | 1,75% | 17 500 PLN |
| Jakub | 1,75% | 17 500 PLN |
| **Razem** | **7,00%** | **70 000 PLN** |

### 4.3. Uzasadnienie formy wynagrodzenia

Warunkowe prawo do zysku (zamiast opłaty pieniężnej z góry) jest uzasadnione:

1. **Twórcy są jednocześnie członkami zarządu i wspólnikami** -- jako zarząd mają interes w komercjalizacji platformy, a jako wspólnicy partycypują w zysku spółki. Członkowie zarządu mogą ustalać wynagrodzenie za świadczenia na rzecz spółki, w tym za udzielenie licencji na IP, którego są twórcami.
2. **Brak obciążenia płynnościowego** -- spółka nie ponosi wydatku z góry, co jest korzystne na etapie wzrostu.
3. **Licencja nie jest nieodpłatna** -- ekwiwalentem jest realne, wymierne prawo do udziału w zysku, co odpiera zarzut niedopłaconego świadczenia na rzecz spółki ([pit.pl -- Odpłatność niepieniężna za pełnienie funkcji członka zarządu](https://www.pit.pl/aktualnosci/odplatnosc-niepieniezna-za-pelnienie-funkcji-czlonka-zarzadu-a-przychod-spolki-1008439)).
4. **Odrębność od dywidendy** -- premia stanowi wynagrodzenie za wkład twórczy (kod, architektura, know-how), a nie za posiadanie udziałów. Prawo do dywidendy pozostaje nienaruszone i wynika z odrębnej podstawy prawnej (KSH art. 191).
5. **Alignment interesów** -- powiązanie wynagrodzenia z zyskiem motywuje twórców (= zarząd) do maksymalizacji wartości platformy.

### 4.4. Adekwatność wynagrodzenia

| | Wartość |
|--|---------|
| Godziwa wartość licencji (sekcja 3.3) | ~290 000 PLN |
| Wynagrodzenie przy zysku 1M PLN | 70 000 PLN (24% godziwej wartości) |
| Wynagrodzenie przy zysku 2M PLN | 140 000 PLN (48% godziwej wartości) |
| Wynagrodzenie przy zysku 3M PLN | 210 000 PLN (72% godziwej wartości) |
| Wynagrodzenie przy zysku 5M PLN | 350 000 PLN (121% godziwej wartości) |

Warunkowy charakter wynagrodzenia (wypłata tylko przy osiągnięciu 1M PLN zysku) uzasadnia, że przy niższych poziomach zysku wynagrodzenie może być niższe od godziwej wartości licencji. Ryzyko nieosiągnięcia warunku ponoszą licencjodawcy.

---

## 5. UZASADNIENIE STRUKTURY TRANSAKCJI

### 5.1. Dlaczego licencja nie może być nieodpłatna

1. **Realna wartość ekonomiczna**: koszt odtworzenia platformy od zera to minimum 1 073 000 PLN. Godziwa wartość licencji wynosi ~290 000 PLN.
2. **2 558 godzin udokumentowanej pracy**: historia git potwierdza ~303 unikalne dni kodowania w ciągu 23 miesięcy.
3. **Ryzyko podatkowe**: nieodpłatne przekazanie licencji o wymiernej wartości mogłoby zostać zakwestionowane przez organy skarbowe jako świadczenie nieodpłatne stanowiące przychód spółki (art. 12 ust. 1 pkt 2 ustawy o CIT). Wyjątek dla wspólników pełniących funkcje zarządu dotyczy pracy zarządczej, nie przeniesienia/licencjonowania odrębnych aktywów IP ([infor.pl -- Nieodpłatne świadczenie dla spółki](https://ksiegowosc.infor.pl/podatki/cit/cit/najczestsze-problemy/318440,Nieodplatne-swiadczenie-dla-spolki-praca-czlonka-zarzadu-bez-wynagrodzenia.html)).

### 5.2. Dlaczego wynagrodzenie warunkowe (a nie opłata z góry)

1. **Spółka na etapie wzrostu** -- opłata z góry obciążyłaby płynność nieproporcjonalnie do bieżących przychodów.
2. **Warunkowe prawo do zysku jest standardowym ekwiwalentem** w transakcjach licencyjnych, szczególnie gdy licencjodawca jest jednocześnie zaangażowany w komercjalizację.
3. **Status Meta Tech Provider**: produkcyjny dostęp do Instagram Business API stanowi barierę wejścia -- twórcy mają interes, by platforma (i spółka) odniosła sukces.
4. **Dojrzałość produkcyjna**: platforma działa na produkcji z 4-warstwowym bezpieczeństwem, automatycznym wdrożeniem i monitoringiem -- to nie jest prototyp, a jego wartość rośnie z przychodami spółki.

---

## 6. PODSUMOWANIE

| Element | Wartość |
|---------|---------|
| Pełna wartość IP (koszt odtworzenia, stawki junior/QA) | ~1 159 000 PLN |
| Dyskonto za licencję niewyłączną + as-is | -75% |
| **Godziwa wartość licencji** | **~290 000 PLN** |
| Forma wynagrodzenia | Warunkowe prawo do 7% zysku netto (3,5% + 1,75% + 1,75%) |
| Warunek wypłaty | Zysk netto > 1 000 000 PLN w pierwszym roku |
| Opłata pieniężna z góry | **Brak** |
| **Wartość WNiP do ujęcia w księgach** | **~290 000 PLN** |

---

## ŹRÓDŁA

### Raport techniczny
- **ip-valuation-comprehensive-feb-2026.md** -- szczegółowa inwentaryzacja, 3 metody wyceny, pełne dane źródłowe (zaktualizowany marzec 2026)
- Historia git: 2 141 commitów non-merge, ~303 unikalne dni, 2 558,2 godzin

### Metodologia wyceny IP
- [WIPO -- Wycena własności intelektualnej](https://www.wipo.int/en/web/business/ip-valuation)
- [WIPO -- Metoda kosztowa](https://www.wipo.int/web-publications/intellectual-property-valuation-basics-for-technology-transfer-professionals/en/4-the-cost-method.html)
- [WIPO -- Metoda rynkowa](https://www.wipo.int/web-publications/intellectual-property-valuation-basics-for-technology-transfer-professionals/en/5-the-market-approach.html)

### Uzasadnienie dyskonta za wyłączność
- [LES High Tech Royalty Survey 2021 -- IPWatchdog](https://ipwatchdog.com/2022/10/03/les-2021-royalty-survey-reports-licensing-market-update-look-back-les-royalty-valuation-method-making/) -- jedyne źródło branżowe kwantyfikujące premię za wyłączność
- [Georgia-Pacific Factors -- IP Glossary](https://www.ipglossary.com/glossary/georgia-pacific-factors/) -- czynnik 4: charakter licencji (wyłączna vs. niewyłączna)
- [PatentPC -- IP Valuation on Licensing](https://patentpc.com/blog/the-impact-of-ip-valuation-on-licensing-deal-terms-and-royalties)
- [FasterCapital -- License Valuation](https://fastercapital.com/content/License-valuation--How-to-Value-Your-License-and-Maximize-Your-Intellectual-Property-Value.html)
- [James & Wells -- Licence Terms](https://www.jamesandwells.com/intl/licence-terms/)

### Uzasadnienie dyskonta za brak wsparcia
- [Brainsell -- Maintenance Fees: What Are You Actually Paying For?](https://www.brainsell.com/blog/maintenance-fees-what-are-you-actually-paying-for/) -- standard 15-25% rocznie
- [SOLTECH -- Software Maintenance Costs & Support](https://soltech.net/software-support-and-maintenance-costs/)
- [Avasant -- High Software Maintenance Fees](https://avasant.com/report/high-software-maintenance-fees-and-what-to-do-about-them/)
- [SoftwareOne -- How to Save Costs on Support and Maintenance](https://www.softwareone.com/en/blog/articles/2020/11/30/how-can-you-save-costs-on-support-and-maintenance) -- typowa stawka ~20% rocznie

### Stawki rynkowe (netto B2B, Polska 2025-2026)
- [BulldogJob -- Raport Społeczności IT 2025](https://bulldogjob.com/it-report/2025/salaries)
- [BulldogJob -- Java Developer zarobki](https://bulldogjob.pl/readme/java-developer-praca-i-zarobki-w-polsce)
- [BulldogJob -- DevOps zarobki](https://bulldogjob.pl/readme/devops-praca-i-zarobki-w-polsce)
- [Glassdoor -- Senior Angular Developer Polska](https://www.glassdoor.com/Salaries/poland-senior-angular-developer-salary-SRCH_IL.0,6_IN193_KO7,31_IP2.htm)
- [Edge1s -- Ile zarabia programista](https://edge1s.com/blog/how-much-does-a-programmer-earn/)

### Struktura transakcji -- świadczenia nieodpłatne i ekwiwalent
- [pit.pl -- Odpłatność niepieniężna za pełnienie funkcji członka zarządu a przychód spółki](https://www.pit.pl/aktualnosci/odplatnosc-niepieniezna-za-pelnienie-funkcji-czlonka-zarzadu-a-przychod-spolki-1008439)
- [infor.pl -- Nieodpłatne świadczenie dla spółki -- praca członka zarządu bez wynagrodzenia](https://ksiegowosc.infor.pl/podatki/cit/cit/najczestsze-problemy/318440,Nieodplatne-swiadczenie-dla-spolki-praca-czlonka-zarzadu-bez-wynagrodzenia.html)
- [Poradnik Przedsiębiorcy -- Nieodpłatne świadczenie w działalności gospodarczej](https://poradnikprzedsiebiorcy.pl/-nieodplatne-swiadczenie-w-dzialalnosci-gospodarczej)
- [GOFIN -- Licencje w stosunkach między spółką a wspólnikami](https://www.gofin.pl/firma/17,2,120,235082,licencje-w-stosunkach-miedzy-spolka-a-wspolnikami.html)

### Porównywalne rynkowe
- [Vestbee -- CEE SaaS Index Q4 2025](https://www.vestbee.com/insights/articles/cee-saa-s-index-q4-2025-update) (mediana 4,31x ARR)
- [GetLatka -- NapoleonCat](https://getlatka.com/companies/napoleoncat) ($1,1M ARR / $3,1M wycena)

---

*Dokument sporządzony na potrzeby ustalenia wartości początkowej WNiP (licencji na oprogramowanie) w ewidencji księgowej spółki, zgodnie z art. 3 ust. 1 pkt 14 ustawy o rachunkowości.*
