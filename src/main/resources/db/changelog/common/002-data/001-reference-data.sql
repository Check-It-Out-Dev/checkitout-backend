-- ============================================================================
-- 001-REFERENCE-DATA: PRODUCTION REFERENCE DATA FOR ALL ENVIRONMENTS
-- ============================================================================
-- Essential data that should exist in production, test, and development
-- No test users or sample data here - only real reference values
-- ============================================================================

-- liquibase formatted sql
-- changeset system:001-insert-reference-data

-- ============================================================================
-- CITIES - Polish cities with states
-- ============================================================================

-- Major cities
INSERT INTO city (name, state, country) VALUES
    ('Warszawa', 'Mazowieckie', 'Polska'),
    ('Kraków', 'Małopolskie', 'Polska'),
    ('Wrocław', 'Dolnośląskie', 'Polska'),
    ('Trójmiasto (Gdańsk, Gdynia, Sopot)', 'Pomorskie', 'Polska'),
    ('Poznań', 'Wielkopolskie', 'Polska'),
    ('Łódź', 'Łódzkie', 'Polska'),
    ('Katowice', 'Śląskie', 'Polska'),
    ('Szczecin', 'Zachodniopomorskie', 'Polska'),
    ('Bydgoszcz', 'Kujawsko-pomorskie', 'Polska'),
    ('Lublin', 'Lubelskie', 'Polska'),
    ('Białystok', 'Podlaskie', 'Polska'),
    ('Rzeszów', 'Podkarpackie', 'Polska'),
    ('Częstochowa', 'Śląskie', 'Polska'),
    ('Radom', 'Mazowieckie', 'Polska'),
    ('Toruń', 'Kujawsko-pomorskie', 'Polska'),
    ('Sosnowiec', 'Śląskie', 'Polska'),
    ('Gliwice', 'Śląskie', 'Polska'),
    ('Zabrze', 'Śląskie', 'Polska'),
    ('Tychy', 'Śląskie', 'Polska'),
    ('Bielsko-Biała', 'Śląskie', 'Polska'),
    ('Olsztyn', 'Warmińsko-mazurskie', 'Polska'),
    ('Opole', 'Opolskie', 'Polska'),
    ('Kielce', 'Świętokrzyskie', 'Polska'),
    ('Zielona Góra', 'Lubuskie', 'Polska'),
    ('Gorzów Wielkopolski', 'Lubuskie', 'Polska'),
    ('Płock', 'Mazowieckie', 'Polska'),
    ('Elbląg', 'Warmińsko-mazurskie', 'Polska'),
    ('Koszalin', 'Zachodniopomorskie', 'Polska'),
    ('Legnica', 'Dolnośląskie', 'Polska'),
    ('Wałbrzych', 'Dolnośląskie', 'Polska'),
    ('Tarnów', 'Małopolskie', 'Polska'),
    ('Nowy Sącz', 'Małopolskie', 'Polska'),
    ('Rybnik', 'Śląskie', 'Polska'),
    ('Jaworzno', 'Śląskie', 'Polska'),
    ('Dąbrowa Górnicza', 'Śląskie', 'Polska'),
    ('Ciechanów', 'Mazowieckie', 'Polska'),
    ('Siedlce', 'Mazowieckie', 'Polska'),
    ('Kalisz', 'Wielkopolskie', 'Polska'),
    ('Konin', 'Wielkopolskie', 'Polska'),
    ('Suwałki', 'Podlaskie', 'Polska'),
    ('Chełm', 'Lubelskie', 'Polska'),
    ('Przemyśl', 'Podkarpackie', 'Polska'),
    ('Piotrków Trybunalski', 'Łódzkie', 'Polska'),
    ('Ełk', 'Warmińsko-mazurskie', 'Polska'),
    ('Słupsk', 'Pomorskie', 'Polska'),
    ('Ostrowiec Świętokrzyski', 'Świętokrzyskie', 'Polska'),
    ('Kędzierzyn-Koźle', 'Opolskie', 'Polska'),
    ('Jelenia Góra', 'Dolnośląskie', 'Polska'),
    ('Stalowa Wola', 'Podkarpackie', 'Polska'),
    ('Łomża', 'Podlaskie', 'Polska');

-- ============================================================================
-- CURRENCIES
-- ============================================================================

INSERT INTO currency (name, iso_code, sign, country_code) VALUES
    ('Euro', 'EUR', '€', 'DE'),
    ('Polish Zloty', 'PLN', 'zł', 'PL'),
    ('US Dollar', 'USD', '$', 'US');

-- ============================================================================
-- PLATFORMS
-- ============================================================================

INSERT INTO platform (name, logo_url, active) VALUES
    ('Instagram', 'https://upload.wikimedia.org/wikipedia/commons/9/95/Instagram_logo_2022.svg', true),
    ('Facebook', 'https://upload.wikimedia.org/wikipedia/commons/5/51/Facebook_f_logo_%282019%29.svg', false),
    ('YouTube', 'https://upload.wikimedia.org/wikipedia/commons/thumb/2/20/YouTube_2024.svg/240px-YouTube_2024.svg.png', false),
    ('TikTok', 'https://upload.wikimedia.org/wikipedia/en/a/a9/TikTok_logo.svg', false);

-- ============================================================================
-- CONTENT TYPES
-- ============================================================================

INSERT INTO content_type (name) VALUES
    ('photo'),
    ('reel'),
    ('story'),
    ('video'),
    ('live');

-- ============================================================================
-- PLATFORM CONTENT TYPES
-- ============================================================================

INSERT INTO platform_content_type (platform_id, content_type_id)
SELECT p.id, ct.id FROM platform p, content_type ct
WHERE p.name = 'Instagram' AND ct.name IN ('photo', 'reel', 'story');

INSERT INTO platform_content_type (platform_id, content_type_id)
SELECT p.id, ct.id FROM platform p, content_type ct
WHERE p.name = 'Facebook' AND ct.name IN ('photo', 'video', 'story', 'live');

INSERT INTO platform_content_type (platform_id, content_type_id)
SELECT p.id, ct.id FROM platform p, content_type ct
WHERE p.name = 'YouTube' AND ct.name IN ('video', 'live');

INSERT INTO platform_content_type (platform_id, content_type_id)
SELECT p.id, ct.id FROM platform p, content_type ct
WHERE p.name = 'TikTok' AND ct.name IN ('video', 'live');

-- ============================================================================
-- SERVICE TYPES - Complete list
-- ============================================================================

-- Gastronomia
INSERT INTO service_type (name, description, category) VALUES
    ('Restauracja', 'Lokal gastronomiczny oferujący pełne menu dań i napojów', 'Gastronomia'),
    ('Kawiarnia', 'Lokal serwujący kawę, herbatę i lekkie przekąski', 'Gastronomia'),
    ('Bar', 'Lokal rozrywkowy serwujący napoje alkoholowe', 'Gastronomia'),
    ('Cukiernia', 'Zakład produkujący i sprzedający wyroby cukiernicze', 'Gastronomia'),
    ('Lodziarnia', 'Lokal sprzedający lody i desery mrożone', 'Gastronomia'),
    ('Food Truck', 'Mobilny punkt gastronomiczny', 'Gastronomia'),
    ('Usługi cateringowe', 'Firma świadcząca usługi gastronomiczne na zewnątrz', 'Gastronomia');

-- Moda
INSERT INTO service_type (name, description, category) VALUES
    ('Sklep odzieżowy', 'Sklep detaliczny z odzieżą i akcesoriami modowymi', 'Moda'),
    ('Sklep z biżuterią', 'Sklep detaliczny z biżuterią i zegarkami', 'Moda'),
    ('Sklep obuwniczy', 'Sklep detaliczny z obuwiem', 'Moda'),
    ('Sklep sportowy', 'Sklep z artykułami i sprzętem sportowym', 'Moda');

-- Uroda
INSERT INTO service_type (name, description, category) VALUES
    ('Studio urody', 'Zakład świadczący zabiegi kosmetyczne i usługi pielęgnacyjne', 'Uroda'),
    ('Salon fryzjerski', 'Zakład świadczący usługi fryzjerskie i pielęgnację włosów', 'Uroda'),
    ('Sklep z kosmetykami', 'Sklep detaliczny z kosmetykami i produktami beauty', 'Uroda'),
    ('Salon paznokci', 'Zakład świadczący usługi manicure i pedicure', 'Uroda'),
    ('Barber', 'Zakład fryzjerski dla mężczyzn', 'Uroda'),
    ('Studio rzęs i brwi', 'Zakład specjalizujący się w stylizacji rzęs i brwi', 'Uroda'),
    ('Gabinet masażu', 'Zakład świadczący usługi masażu', 'Uroda');

-- Zdrowie i fitness
INSERT INTO service_type (name, description, category) VALUES
    ('Siłownia i fitness', 'Obiekt z wyposażeniem do ćwiczeń i zajęć fitness', 'Zdrowie i fitness'),
    ('Studio jogi', 'Miejsce prowadzące zajęcia jogi i pilates', 'Zdrowie i fitness'),
    ('Klinika stomatologiczna', 'Placówka świadcząca usługi dentystyczne', 'Zdrowie i fitness'),
    ('Klinika medycyny estetycznej', 'Placówka świadcząca zabiegi medycyny estetycznej', 'Zdrowie i fitness'),
    ('Dietetyk', 'Specjalista świadczący porady żywieniowe', 'Zdrowie i fitness');

-- Rozrywka
INSERT INTO service_type (name, description, category) VALUES
    ('Park rozrywki', 'Miejsce rozrywki z atrakcjami i przejażdżkami', 'Rozrywka'),
    ('Klub muzyczny', 'Lokal rozrywkowy z muzyką na żywo', 'Rozrywka'),
    ('Escape room', 'Miejsce rozrywki z pokojami zagadek', 'Rozrywka'),
    ('Kręgielnia', 'Obiekt rozrywkowy z torami do kręgli', 'Rozrywka'),
    ('Studio tańca', 'Miejsce prowadzące zajęcia taneczne', 'Rozrywka');

-- Kreatywne
INSERT INTO service_type (name, description, category) VALUES
    ('Studio fotograficzne', 'Zakład świadczący profesjonalne usługi fotograficzne', 'Kreatywne'),
    ('Studio tatuażu', 'Zakład świadczący usługi tatuażu i body art', 'Kreatywne'),
    ('Kwiaciarnia', 'Sklep z kwiatami i kompozycjami roślinnymi', 'Kreatywne');

-- Biznes
INSERT INTO service_type (name, description, category) VALUES
    ('Agencja marketingowa', 'Firma świadcząca usługi marketingowe i reklamowe', 'Biznes'),
    ('Organizacja eventów', 'Firma organizująca imprezy i wydarzenia', 'Biznes'),
    ('Biuro nieruchomości', 'Agencja pośrednictwa w obrocie nieruchomościami', 'Biznes'),
    ('Sklep internetowy', 'Firma prowadząca sprzedaż online', 'Biznes');

-- Turystyka
INSERT INTO service_type (name, description, category) VALUES
    ('Hotel i spa', 'Obiekt noclegowy z usługami spa i wellness', 'Turystyka');

-- Transport
INSERT INTO service_type (name, description, category) VALUES
    ('Wypożyczalnia samochodów', 'Firma wynajmująca pojazdy', 'Transport');

-- Motoryzacja
INSERT INTO service_type (name, description, category) VALUES
    ('Warsztat samochodowy', 'Zakład naprawy i serwisu pojazdów', 'Motoryzacja'),
    ('Myjnia samochodowa', 'Zakład świadczący usługi mycia pojazdów', 'Motoryzacja'),
    ('Auto detailing', 'Zakład profesjonalnej pielęgnacji pojazdów', 'Motoryzacja');

-- Dom i wnętrza
INSERT INTO service_type (name, description, category) VALUES
    ('Sklep z wyposażeniem wnętrz', 'Sklep z meblami i dekoracjami', 'Dom i wnętrza');

-- Edukacja i kultura
INSERT INTO service_type (name, description, category) VALUES
    ('Księgarnia', 'Sklep z książkami i prasą', 'Edukacja i kultura'),
    ('Galeria sztuki', 'Miejsce wystawiające i sprzedające dzieła sztuki', 'Edukacja i kultura');

-- Usługi
INSERT INTO service_type (name, description, category) VALUES
    ('Salon dla zwierząt', 'Zakład świadczący usługi pielęgnacji zwierząt', 'Usługi');

-- Budownictwo i remonty
INSERT INTO service_type (name, description, category) VALUES
    ('Stolarnia', 'Zakład produkujący wyroby z drewna', 'Budownictwo i remonty'),
    ('Firma remontowa', 'Przedsiębiorstwo świadczące usługi remontowe', 'Budownictwo i remonty'),
    ('Firma budowlana', 'Przedsiębiorstwo realizujące prace budowlane', 'Budownictwo i remonty'),
    ('Zakład ślusarski', 'Warsztat wykonujący prace metalowe', 'Budownictwo i remonty'),
    ('Usługi hydrauliczne', 'Firma świadcząca usługi instalacji wodnych', 'Budownictwo i remonty'),
    ('Usługi elektryczne', 'Firma świadcząca usługi instalacji elektrycznych', 'Budownictwo i remonty'),
    ('Usługi malarskie', 'Firma świadcząca usługi malowania wnętrz', 'Budownictwo i remonty'),
    ('Projektowanie wnętrz', 'Firma projektująca aranżacje wnętrz', 'Budownictwo i remonty'),
    ('Zakład kamieniarski', 'Warsztat obróbki kamienia', 'Budownictwo i remonty'),
    ('Tapicernia', 'Zakład świadczący usługi tapicerskie', 'Budownictwo i remonty');

-- Other category
INSERT INTO service_type (name, description, category) VALUES
    ('Inne', 'Usługa spoza głównych kategorii', 'Inne');

-- ============================================================================
-- DICTIONARY ENTRIES - UI Translations
-- ============================================================================

INSERT INTO dictionary_entries (entry_key, value, language_code, category, updater_id) VALUES
    -- Greetings
    ('hello', 'Hello', 'en', 'greetings', 'system'),
    ('goodbye', 'Goodbye', 'en', 'greetings', 'system'),
    ('hello', 'Cześć', 'pl', 'greetings', 'system'),
    ('goodbye', 'Do widzenia', 'pl', 'greetings', 'system'),
    
    -- Common Phrases
    ('thank_you', 'Thank you', 'en', 'common_phrases', 'system'),
    ('please', 'Please', 'en', 'common_phrases', 'system'),
    ('thank_you', 'Dziękuję', 'pl', 'common_phrases', 'system'),
    ('please', 'Proszę', 'pl', 'common_phrases', 'system'),
    
    -- UI Labels
    ('submit', 'Submit', 'en', 'ui_labels', 'system'),
    ('cancel', 'Cancel', 'en', 'ui_labels', 'system'),
    ('submit', 'Wyślij', 'pl', 'ui_labels', 'system'),
    ('cancel', 'Anuluj', 'pl', 'ui_labels', 'system'),
    ('save', 'Save', 'en', 'ui_labels', 'system'),
    ('save', 'Zapisz', 'pl', 'ui_labels', 'system'),
    ('delete', 'Delete', 'en', 'ui_labels', 'system'),
    ('delete', 'Usuń', 'pl', 'ui_labels', 'system'),
    ('edit', 'Edit', 'en', 'ui_labels', 'system'),
    ('edit', 'Edytuj', 'pl', 'ui_labels', 'system'),
    
    -- Errors
    ('error_404', 'Page not found', 'en', 'errors', 'system'),
    ('error_500', 'Internal server error', 'en', 'errors', 'system'),
    ('error_404', 'Strona nie znaleziona', 'pl', 'errors', 'system'),
    ('error_500', 'Błąd serwera', 'pl', 'errors', 'system'),
    ('error_401', 'Unauthorized', 'en', 'errors', 'system'),
    ('error_401', 'Brak autoryzacji', 'pl', 'errors', 'system'),
    ('error_403', 'Forbidden', 'en', 'errors', 'system'),
    ('error_403', 'Dostęp zabroniony', 'pl', 'errors', 'system');

-- ============================================================================
-- ADMIN USERS - Moved to separate file 003-admin-users.sql
-- ============================================================================
-- Admin users are now in a separate changeset for better control

-- rollback DELETE FROM dictionary_entries;
-- rollback DELETE FROM service_type;
-- rollback DELETE FROM platform_content_type;
-- rollback DELETE FROM content_type;
-- rollback DELETE FROM platform;
-- rollback DELETE FROM currency;
-- rollback DELETE FROM city;
