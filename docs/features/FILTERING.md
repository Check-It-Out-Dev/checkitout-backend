# Enhanced Filtering Features

Poniższa dokumentacja opisuje nowe funkcjonalności filtrowania dodane do systemu partnership opportunities.

## 1. Filtrowanie po polach boolean

### Problem
Dotychczas filtrowanie po polach typu boolean (np. `active`) nie działało poprawnie.

### Rozwiązanie
Dodano obsługę pól boolean w `SpecificationBuilder`. Teraz możesz filtrować po polach boolean używając wartości `true` lub `false`.

### Przykłady użycia

**Pobierz tylko aktywne partnership opportunities:**
```
GET /partnership-opportunity/paged?active=true
```

**Pobierz tylko nieaktywne partnership opportunities:**
```
GET /partnership-opportunity/paged?active=false
```

**Połącz z innymi filtrami:**
```
GET /partnership-opportunity/paged?active=true&compensationType=CASH
```

## 2. Operacja NOT (negacja)

### Problem
Brakowało możliwości filtrowania z negacją - np. pobranie wszystkich opportunities OPRÓCZ tych należących do konkretnej firmy.

### Rozwiązanie
Dodano obsługę operatora NOT używając wykrzyknika `!` na końcu nazwy pola.

### Składnia
```
<pole>! = <wartość>
```

### Przykłady użycia

**Pobierz opportunities które NIE należą do firmy o ID=1:**
```
GET /partnership-opportunity/paged?company.id!=1
```

**Pobierz opportunities które NIE są typu CASH:**
```
GET /partnership-opportunity/paged?compensationType!=CASH
```

**Pobierz opportunities które NIE są aktywne:**
```
GET /partnership-opportunity/paged?active!=true
```

**Kombinacja filtrów z NOT:**
```
GET /partnership-opportunity/paged?active=true&company.id!=1&compensationType!=BARTER
```

## 3. Obsługiwane typy danych

Operacja NOT działa ze wszystkimi typami danych obsługiwanymi przez system:

- **String** (np. `name!=Test`)
- **Number** (np. `company.id!=1`, `compensationAmountMin!=100`)
- **Boolean** (np. `active!=true`)
- **Enum** (np. `compensationType!=CASH`)
- **Date/DateTime** (np. `startDate!=2025-01-01T10:00:00`)
- **Relationship fields** (np. `city.name!=Warsaw`)

## 4. Przykłady zaawansowanych zapytań

### Scenariusz 1: Aktywne opportunities innych firm
```
GET /partnership-opportunity/paged?active=true&company.id!=123
```

### Scenariusz 2: Wszystkie opportunities oprócz BARTER w Warszawie
```
GET /partnership-opportunity/paged?city.name=Warsaw&compensationType!=BARTER
```

### Scenariusz 3: Opportunities które NIE są aktywne i NIE należą do konkretnej firmy
```
GET /partnership-opportunity/paged?active!=true&company.id!=456
```

## 5. Implementacja techniczna

### Zmiany w SpecificationBuilder
1. **Obsługa boolean**: Dodano sprawdzanie `fieldType.equals(boolean.class) || fieldType.equals(Boolean.class)`
2. **Operacja NOT**: Sprawdzanie czy nazwa pola kończy się `!` i aplikowanie `criteriaBuilder.not(predicate)`

### Logowanie
System loguje informacje o zastosowaniu operacji NOT:
```
DEBUG: Applied NOT operation to field: company.id
```

## 6. Testowanie

Dodano testy jednostkowe w `PartnershipOpportunityFilteringTest` które demonstrują:
- Filtrowanie po polach boolean
- Operację NOT dla różnych typów danych
- Kombinowanie filtrów z NOT

## 7. Backward Compatibility

Wszystkie istniejące zapytania będą działać bez zmian. Nowe funkcjonalności są w pełni kompatybilne wstecz.

## 8. Ograniczenia

1. **NOT operation**: Nie działa z filtrami dat typu `From`/`To` (np. `startDateFrom!` nie jest obsługiwane)
2. **Case sensitivity**: Nazwy pól są case-sensitive
3. **Validation**: System nie waliduje czy podana wartość dla NOT operation ma sens logicznie

## 9. Troubleshooting

### Problem: active=true nie działa
**Rozwiązanie**: Upewnij się, że używasz dokładnie `active=true` lub `active=false` (case-sensitive)

### Problem: NOT operation nie działa
**Rozwiązanie**: 
- Sprawdź czy wykrzyknik jest na końcu nazwy pola: `company.id!` ✅, `!company.id` ❌
- Upewnij się, że pole istnieje (bez wykrzyknika)

### Problem: Kombinacja filtrów nie działa
**Rozwiązanie**: Każdy parametr query powinien być oddzielony `&`:
```
?active=true&company.id!=1&compensationType=CASH
```
