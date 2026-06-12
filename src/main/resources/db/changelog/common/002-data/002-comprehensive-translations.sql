-- liquibase formatted sql
-- changeset system:comprehensive-translations
-- Comprehensive translation dictionary for all enums, types, and UI elements
-- Consolidates all translations in one file for easier pre-production management
-- Consolidated from: 002-enum-translations.sql, 005-deletion-blocker-translations.sql, 006-consent-translations.sql

INSERT INTO dictionary_entries (entry_key, value, language_code, category, updater_id) VALUES

-- ============================================================================
-- ENUM TRANSLATIONS - ServiceType (from reference data)
-- ============================================================================
    -- Gastronomia - English
    ('SERVICE_TYPE_RESTAURACJA', 'Restaurant', 'en', 'service_types', 'system'),
    ('SERVICE_TYPE_KAWIARNIA', 'Cafe', 'en', 'service_types', 'system'),
    ('SERVICE_TYPE_BAR', 'Bar', 'en', 'service_types', 'system'),
    ('SERVICE_TYPE_CUKIERNIA', 'Pastry Shop', 'en', 'service_types', 'system'),
    ('SERVICE_TYPE_LODZIARNIA', 'Ice Cream Shop', 'en', 'service_types', 'system'),
    ('SERVICE_TYPE_FOOD_TRUCK', 'Food Truck', 'en', 'service_types', 'system'),
    ('SERVICE_TYPE_USŁUGI_CATERINGOWE', 'Catering Services', 'en', 'service_types', 'system'),

    -- Moda - English
    ('SERVICE_TYPE_SKLEP_ODZIEŻOWY', 'Clothing Store', 'en', 'service_types', 'system'),
    ('SERVICE_TYPE_SKLEP_Z_BIŻUTERIĄ', 'Jewelry Store', 'en', 'service_types', 'system'),
    ('SERVICE_TYPE_SKLEP_OBUWNICZY', 'Shoe Store', 'en', 'service_types', 'system'),
    ('SERVICE_TYPE_SKLEP_SPORTOWY', 'Sports Store', 'en', 'service_types', 'system'),

    -- Uroda - English
    ('SERVICE_TYPE_STUDIO_URODY', 'Beauty Studio', 'en', 'service_types', 'system'),
    ('SERVICE_TYPE_SALON_FRYZJERSKI', 'Hair Salon', 'en', 'service_types', 'system'),
    ('SERVICE_TYPE_SKLEP_Z_KOSMETYKAMI', 'Cosmetics Store', 'en', 'service_types', 'system'),
    ('SERVICE_TYPE_SALON_PAZNOKCI', 'Nail Salon', 'en', 'service_types', 'system'),
    ('SERVICE_TYPE_BARBER', 'Barber Shop', 'en', 'service_types', 'system'),
    ('SERVICE_TYPE_STUDIO_RZĘS_I_BRWI', 'Lash & Brow Studio', 'en', 'service_types', 'system'),
    ('SERVICE_TYPE_GABINET_MASAŻU', 'Massage Studio', 'en', 'service_types', 'system'),

    -- Zdrowie i fitness - English
    ('SERVICE_TYPE_SIŁOWNIA_I_FITNESS', 'Gym & Fitness', 'en', 'service_types', 'system'),
    ('SERVICE_TYPE_STUDIO_JOGI', 'Yoga Studio', 'en', 'service_types', 'system'),
    ('SERVICE_TYPE_KLINIKA_STOMATOLOGICZNA', 'Dental Clinic', 'en', 'service_types', 'system'),
    ('SERVICE_TYPE_KLINIKA_MEDYCYNY_ESTETYCZNEJ', 'Aesthetic Medicine Clinic', 'en', 'service_types', 'system'),
    ('SERVICE_TYPE_DIETETYK', 'Dietitian', 'en', 'service_types', 'system'),

    -- Biznes - English
    ('SERVICE_TYPE_AGENCJA_MARKETINGOWA', 'Marketing Agency', 'en', 'service_types', 'system'),
    ('SERVICE_TYPE_ORGANIZACJA_EVENTÓW', 'Event Organization', 'en', 'service_types', 'system'),
    ('SERVICE_TYPE_BIURO_NIERUCHOMOŚCI', 'Real Estate Agency', 'en', 'service_types', 'system'),
    ('SERVICE_TYPE_SKLEP_INTERNETOWY', 'Online Store', 'en', 'service_types', 'system'),

    -- Turystyka - English
    ('SERVICE_TYPE_HOTEL_I_SPA', 'Hotel & Spa', 'en', 'service_types', 'system'),

    -- Other key service types - English
    ('SERVICE_TYPE_INNE', 'Other', 'en', 'service_types', 'system'),

    -- Service Type Descriptions - English
    ('SERVICE_TYPE_RESTAURACJA_DESC', 'Gastronomic establishment offering full menu of food and drinks', 'en', 'service_types', 'system'),
    ('SERVICE_TYPE_KAWIARNIA_DESC', 'Establishment serving coffee, tea and light snacks', 'en', 'service_types', 'system'),
    ('SERVICE_TYPE_BAR_DESC', 'Entertainment venue serving alcoholic beverages', 'en', 'service_types', 'system'),
    ('SERVICE_TYPE_CUKIERNIA_DESC', 'Establishment producing and selling confectionery products', 'en', 'service_types', 'system'),
    ('SERVICE_TYPE_LODZIARNIA_DESC', 'Establishment selling ice cream and frozen desserts', 'en', 'service_types', 'system'),
    ('SERVICE_TYPE_FOOD_TRUCK_DESC', 'Mobile food service point', 'en', 'service_types', 'system'),
    ('SERVICE_TYPE_USŁUGI_CATERINGOWE_DESC', 'Company providing external catering services', 'en', 'service_types', 'system'),
    ('SERVICE_TYPE_SKLEP_ODZIEŻOWY_DESC', 'Retail store with clothing and fashion accessories', 'en', 'service_types', 'system'),
    ('SERVICE_TYPE_SKLEP_Z_BIŻUTERIĄ_DESC', 'Retail store with jewelry and watches', 'en', 'service_types', 'system'),
    ('SERVICE_TYPE_SKLEP_OBUWNICZY_DESC', 'Retail store with footwear', 'en', 'service_types', 'system'),
    ('SERVICE_TYPE_SKLEP_SPORTOWY_DESC', 'Store with sports articles and equipment', 'en', 'service_types', 'system'),
    ('SERVICE_TYPE_STUDIO_URODY_DESC', 'Establishment providing cosmetic treatments and care services', 'en', 'service_types', 'system'),
    ('SERVICE_TYPE_SALON_FRYZJERSKI_DESC', 'Establishment providing hairdressing services and hair care', 'en', 'service_types', 'system'),
    ('SERVICE_TYPE_SKLEP_Z_KOSMETYKAMI_DESC', 'Retail store with cosmetics and beauty products', 'en', 'service_types', 'system'),
    ('SERVICE_TYPE_SALON_PAZNOKCI_DESC', 'Establishment providing manicure and pedicure services', 'en', 'service_types', 'system'),
    ('SERVICE_TYPE_BARBER_DESC', 'Men''s hairdressing establishment', 'en', 'service_types', 'system'),
    ('SERVICE_TYPE_STUDIO_RZĘS_I_BRWI_DESC', 'Establishment specializing in eyelash and eyebrow styling', 'en', 'service_types', 'system'),
    ('SERVICE_TYPE_GABINET_MASAŻU_DESC', 'Establishment providing massage services', 'en', 'service_types', 'system'),
    ('SERVICE_TYPE_SIŁOWNIA_I_FITNESS_DESC', 'Facility with exercise equipment and fitness classes', 'en', 'service_types', 'system'),
    ('SERVICE_TYPE_STUDIO_JOGI_DESC', 'Place conducting yoga and pilates classes', 'en', 'service_types', 'system'),
    ('SERVICE_TYPE_KLINIKA_STOMATOLOGICZNA_DESC', 'Facility providing dental services', 'en', 'service_types', 'system'),
    ('SERVICE_TYPE_KLINIKA_MEDYCYNY_ESTETYCZNEJ_DESC', 'Facility providing aesthetic medicine treatments', 'en', 'service_types', 'system'),
    ('SERVICE_TYPE_DIETETYK_DESC', 'Specialist providing nutritional advice', 'en', 'service_types', 'system'),
    ('SERVICE_TYPE_AGENCJA_MARKETINGOWA_DESC', 'Company providing marketing and advertising services', 'en', 'service_types', 'system'),
    ('SERVICE_TYPE_ORGANIZACJA_EVENTÓW_DESC', 'Company organizing events and occasions', 'en', 'service_types', 'system'),
    ('SERVICE_TYPE_BIURO_NIERUCHOMOŚCI_DESC', 'Real estate brokerage agency', 'en', 'service_types', 'system'),
    ('SERVICE_TYPE_SKLEP_INTERNETOWY_DESC', 'Company conducting online sales', 'en', 'service_types', 'system'),
    ('SERVICE_TYPE_HOTEL_I_SPA_DESC', 'Accommodation facility with spa and wellness services', 'en', 'service_types', 'system'),
    ('SERVICE_TYPE_INNE_DESC', 'Service outside main categories', 'en', 'service_types', 'system'),

    -- Service Categories - English
    ('SERVICE_CATEGORY_GASTRONOMIA', 'Gastronomy', 'en', 'service_categories', 'system'),
    ('SERVICE_CATEGORY_MODA', 'Fashion', 'en', 'service_categories', 'system'),
    ('SERVICE_CATEGORY_URODA', 'Beauty', 'en', 'service_categories', 'system'),
    ('SERVICE_CATEGORY_ZDROWIE_I_FITNESS', 'Health & Fitness', 'en', 'service_categories', 'system'),
    ('SERVICE_CATEGORY_ROZRYWKA', 'Entertainment', 'en', 'service_categories', 'system'),
    ('SERVICE_CATEGORY_KREATYWNE', 'Creative', 'en', 'service_categories', 'system'),
    ('SERVICE_CATEGORY_BIZNES', 'Business', 'en', 'service_categories', 'system'),
    ('SERVICE_CATEGORY_TURYSTYKA', 'Tourism', 'en', 'service_categories', 'system'),
    ('SERVICE_CATEGORY_TRANSPORT', 'Transport', 'en', 'service_categories', 'system'),
    ('SERVICE_CATEGORY_MOTORYZACJA', 'Automotive', 'en', 'service_categories', 'system'),
    ('SERVICE_CATEGORY_DOM_I_WNĘTRZA', 'Home & Interior', 'en', 'service_categories', 'system'),
    ('SERVICE_CATEGORY_EDUKACJA_I_KULTURA', 'Education & Culture', 'en', 'service_categories', 'system'),
    ('SERVICE_CATEGORY_USŁUGI', 'Services', 'en', 'service_categories', 'system'),
    ('SERVICE_CATEGORY_BUDOWNICTWO_I_REMONTY', 'Construction & Renovation', 'en', 'service_categories', 'system'),
    ('SERVICE_CATEGORY_INNE', 'Other', 'en', 'service_categories', 'system'),

-- ============================================================================
-- CURRENCY TRANSLATIONS
-- ============================================================================

    -- Currency - English
    ('CURRENCY_EUR', 'Euro', 'en', 'currencies', 'system'),
    ('CURRENCY_PLN', 'Polish Zloty', 'en', 'currencies', 'system'),
    ('CURRENCY_USD', 'US Dollar', 'en', 'currencies', 'system'),

    -- Currency - Polish (keeping originals)
    ('CURRENCY_EUR', 'Euro', 'pl', 'currencies', 'system'),
    ('CURRENCY_PLN', 'Polski Złoty', 'pl', 'currencies', 'system'),
    ('CURRENCY_USD', 'Dolar Amerykański', 'pl', 'currencies', 'system'),

-- ============================================================================
-- CONTENT TYPE TRANSLATIONS
-- ============================================================================

    -- ContentType - English
    ('CONTENT_TYPE_PHOTO', 'Photo', 'en', 'content_types', 'system'),
    ('CONTENT_TYPE_REEL', 'Reel', 'en', 'content_types', 'system'),
    ('CONTENT_TYPE_STORY', 'Story', 'en', 'content_types', 'system'),
    ('CONTENT_TYPE_VIDEO', 'Video', 'en', 'content_types', 'system'),
    ('CONTENT_TYPE_LIVE', 'Live Stream', 'en', 'content_types', 'system'),

    -- ContentType - Polish
    ('CONTENT_TYPE_PHOTO', 'Zdjęcie', 'pl', 'content_types', 'system'),
    ('CONTENT_TYPE_REEL', 'Rolka', 'pl', 'content_types', 'system'),
    ('CONTENT_TYPE_STORY', 'Historia', 'pl', 'content_types', 'system'),
    ('CONTENT_TYPE_VIDEO', 'Film', 'pl', 'content_types', 'system'),
    ('CONTENT_TYPE_LIVE', 'Transmisja na żywo', 'pl', 'content_types', 'system'),

-- ============================================================================
-- ENUM TRANSLATIONS - OpportunityStatus
-- ============================================================================

    -- OpportunityStatus - English
    ('OPPORTUNITY_STATUS_APPLIED', 'Applied', 'en', 'enums', 'system'),
    ('OPPORTUNITY_STATUS_ACCEPTED_BY_COMPANY', 'Accepted by Company', 'en', 'enums', 'system'),
    ('OPPORTUNITY_STATUS_REJECTED_BY_COMPANY', 'Rejected by Company', 'en', 'enums', 'system'),
    ('OPPORTUNITY_STATUS_ACCEPTED_BY_INFLUENCER', 'Accepted by Influencer', 'en', 'enums', 'system'),
    ('OPPORTUNITY_STATUS_REJECTED_BY_INFLUENCER', 'Rejected by Influencer', 'en', 'enums', 'system'),
    ('OPPORTUNITY_STATUS_CONTENT_SEND_TO_ACCEPT', 'Content Sent for Approval', 'en', 'enums', 'system'),
    ('OPPORTUNITY_STATUS_CONTENT_APPROVED', 'Content Approved', 'en', 'enums', 'system'),
    ('OPPORTUNITY_STATUS_CONTENT_REJECTED', 'Content Rejected', 'en', 'enums', 'system'),
    ('OPPORTUNITY_STATUS_CONTENT_POSTED', 'Content Posted', 'en', 'enums', 'system'),
    ('OPPORTUNITY_STATUS_CONTENT_POSTED_REJECTED', 'Posted Content Rejected', 'en', 'enums', 'system'),
    ('OPPORTUNITY_STATUS_TO_BE_PAID', 'To Be Paid', 'en', 'enums', 'system'),
    ('OPPORTUNITY_STATUS_DONE', 'Done', 'en', 'enums', 'system'),

    -- OpportunityStatus - Polish
    ('OPPORTUNITY_STATUS_APPLIED', 'Zgłoszona', 'pl', 'enums', 'system'),
    ('OPPORTUNITY_STATUS_ACCEPTED_BY_COMPANY', 'Zaakceptowana przez firmę', 'pl', 'enums', 'system'),
    ('OPPORTUNITY_STATUS_REJECTED_BY_COMPANY', 'Odrzucona przez firmę', 'pl', 'enums', 'system'),
    ('OPPORTUNITY_STATUS_ACCEPTED_BY_INFLUENCER', 'Zaakceptowana przez influencera', 'pl', 'enums', 'system'),
    ('OPPORTUNITY_STATUS_REJECTED_BY_INFLUENCER', 'Odrzucona przez influencera', 'pl', 'enums', 'system'),
    ('OPPORTUNITY_STATUS_CONTENT_SEND_TO_ACCEPT', 'Treść wysłana do akceptacji', 'pl', 'enums', 'system'),
    ('OPPORTUNITY_STATUS_CONTENT_APPROVED', 'Treść zaakceptowana', 'pl', 'enums', 'system'),
    ('OPPORTUNITY_STATUS_CONTENT_REJECTED', 'Treść odrzucona', 'pl', 'enums', 'system'),
    ('OPPORTUNITY_STATUS_CONTENT_POSTED', 'Treść opublikowana', 'pl', 'enums', 'system'),
    ('OPPORTUNITY_STATUS_CONTENT_POSTED_REJECTED', 'Opublikowana treść odrzucona', 'pl', 'enums', 'system'),
    ('OPPORTUNITY_STATUS_TO_BE_PAID', 'Do wypłaty', 'pl', 'enums', 'system'),
    ('OPPORTUNITY_STATUS_DONE', 'Zakończona', 'pl', 'enums', 'system'),

    -- OpportunityStatus Descriptions - English
    ('OPPORTUNITY_STATUS_APPLIED_DESC', 'Influencer has applied for the campaign and is waiting for company response', 'en', 'enums', 'system'),
    ('OPPORTUNITY_STATUS_ACCEPTED_BY_COMPANY_DESC', 'Company has accepted the application and influencer needs to respond', 'en', 'enums', 'system'),
    ('OPPORTUNITY_STATUS_REJECTED_BY_COMPANY_DESC', 'Company has rejected the application - collaboration ended', 'en', 'enums', 'system'),
    ('OPPORTUNITY_STATUS_ACCEPTED_BY_INFLUENCER_DESC', 'Influencer has accepted and now needs to create content', 'en', 'enums', 'system'),
    ('OPPORTUNITY_STATUS_REJECTED_BY_INFLUENCER_DESC', 'Influencer has rejected the campaign - collaboration ended', 'en', 'enums', 'system'),
    ('OPPORTUNITY_STATUS_CONTENT_SEND_TO_ACCEPT_DESC', 'Content has been submitted and is awaiting company approval', 'en', 'enums', 'system'),
    ('OPPORTUNITY_STATUS_CONTENT_APPROVED_DESC', 'Content has been approved and can now be posted', 'en', 'enums', 'system'),
    ('OPPORTUNITY_STATUS_CONTENT_REJECTED_DESC', 'Content was rejected and needs to be revised and resubmitted', 'en', 'enums', 'system'),
    ('OPPORTUNITY_STATUS_CONTENT_POSTED_DESC', 'Content has been posted on social media and awaits verification', 'en', 'enums', 'system'),
    ('OPPORTUNITY_STATUS_CONTENT_POSTED_REJECTED_DESC', 'Posted content was deemed invalid and needs to be corrected', 'en', 'enums', 'system'),
    ('OPPORTUNITY_STATUS_TO_BE_PAID_DESC', 'Content is approved and payment is being processed', 'en', 'enums', 'system'),
    ('OPPORTUNITY_STATUS_DONE_DESC', 'Collaboration completed successfully - both parties can rate', 'en', 'enums', 'system'),

    -- OpportunityStatus Descriptions - Polish
    ('OPPORTUNITY_STATUS_APPLIED_DESC', 'Influencer zgłosił się do kampanii i czeka na odpowiedź firmy', 'pl', 'enums', 'system'),
    ('OPPORTUNITY_STATUS_ACCEPTED_BY_COMPANY_DESC', 'Firma zaakceptowała zgłoszenie i influencer musi odpowiedzieć', 'pl', 'enums', 'system'),
    ('OPPORTUNITY_STATUS_REJECTED_BY_COMPANY_DESC', 'Firma odrzuciła zgłoszenie - współpraca zakończona', 'pl', 'enums', 'system'),
    ('OPPORTUNITY_STATUS_ACCEPTED_BY_INFLUENCER_DESC', 'Influencer zaakceptował i teraz musi stworzyć treść', 'pl', 'enums', 'system'),
    ('OPPORTUNITY_STATUS_REJECTED_BY_INFLUENCER_DESC', 'Influencer odrzucił kampanię - współpraca zakończona', 'pl', 'enums', 'system'),
    ('OPPORTUNITY_STATUS_CONTENT_SEND_TO_ACCEPT_DESC', 'Treść została przesłana i oczekuje na akceptację firmy', 'pl', 'enums', 'system'),
    ('OPPORTUNITY_STATUS_CONTENT_APPROVED_DESC', 'Treść została zaakceptowana i może zostać opublikowana', 'pl', 'enums', 'system'),
    ('OPPORTUNITY_STATUS_CONTENT_REJECTED_DESC', 'Treść została odrzucona i wymaga poprawek', 'pl', 'enums', 'system'),
    ('OPPORTUNITY_STATUS_CONTENT_POSTED_DESC', 'Treść została opublikowana w mediach społecznościowych i oczekuje weryfikacji', 'pl', 'enums', 'system'),
    ('OPPORTUNITY_STATUS_CONTENT_POSTED_REJECTED_DESC', 'Opublikowana treść została uznana za nieprawidłową i wymaga korekty', 'pl', 'enums', 'system'),
    ('OPPORTUNITY_STATUS_TO_BE_PAID_DESC', 'Treść została zatwierdzona i płatność jest przetwarzana', 'pl', 'enums', 'system'),
    ('OPPORTUNITY_STATUS_DONE_DESC', 'Współpraca zakończona pomyślnie - obie strony mogą ocenić', 'pl', 'enums', 'system'),

-- ============================================================================
-- ENUM TRANSLATIONS - UserType
-- ============================================================================

    -- UserType - English
    ('USER_TYPE_ADMIN', 'Administrator', 'en', 'enums', 'system'),
    ('USER_TYPE_PENDING_ADMIN', 'Pending Administrator', 'en', 'enums', 'system'),
    ('USER_TYPE_INFLUENCER', 'Influencer', 'en', 'enums', 'system'),
    ('USER_TYPE_COMPANY', 'Company', 'en', 'enums', 'system'),

    -- UserType - Polish
    ('USER_TYPE_ADMIN', 'Administrator', 'pl', 'enums', 'system'),
    ('USER_TYPE_PENDING_ADMIN', 'Oczekujący Administrator', 'pl', 'enums', 'system'),
    ('USER_TYPE_INFLUENCER', 'Influencer', 'pl', 'enums', 'system'),
    ('USER_TYPE_COMPANY', 'Firma', 'pl', 'enums', 'system'),

-- ============================================================================
-- ENUM TRANSLATIONS - AccountStatus
-- ============================================================================

    -- AccountStatus - English
    ('ACCOUNT_STATUS_INACTIVE', 'Inactive', 'en', 'enums', 'system'),
    ('ACCOUNT_STATUS_IN_VALIDATION', 'In Validation', 'en', 'enums', 'system'),
    ('ACCOUNT_STATUS_ACTIVE', 'Active', 'en', 'enums', 'system'),
    ('ACCOUNT_STATUS_TO_BE_DELETED', 'To Be Deleted', 'en', 'enums', 'system'),
    ('ACCOUNT_STATUS_DELETED', 'Deleted', 'en', 'enums', 'system'),
    ('ACCOUNT_STATUS_BANNED', 'Banned', 'en', 'enums', 'system'),

    -- AccountStatus - Polish
    ('ACCOUNT_STATUS_INACTIVE', 'Nieaktywne', 'pl', 'enums', 'system'),
    ('ACCOUNT_STATUS_IN_VALIDATION', 'W walidacji', 'pl', 'enums', 'system'),
    ('ACCOUNT_STATUS_ACTIVE', 'Aktywne', 'pl', 'enums', 'system'),
    ('ACCOUNT_STATUS_TO_BE_DELETED', 'Do usunięcia', 'pl', 'enums', 'system'),
    ('ACCOUNT_STATUS_DELETED', 'Usunięte', 'pl', 'enums', 'system'),
    ('ACCOUNT_STATUS_BANNED', 'Zbanowany', 'pl', 'enums', 'system'),

    -- AccountStatus Descriptions - English
    ('ACCOUNT_STATUS_INACTIVE_DESC', 'Account is inactive and cannot be used', 'en', 'enums', 'system'),
    ('ACCOUNT_STATUS_IN_VALIDATION_DESC', 'Account is being validated by administrators', 'en', 'enums', 'system'),
    ('ACCOUNT_STATUS_ACTIVE_DESC', 'Account is active and fully functional', 'en', 'enums', 'system'),
    ('ACCOUNT_STATUS_TO_BE_DELETED_DESC', 'Account is marked for deletion', 'en', 'enums', 'system'),
    ('ACCOUNT_STATUS_DELETED_DESC', 'Account has been permanently deleted', 'en', 'enums', 'system'),
    ('ACCOUNT_STATUS_BANNED_DESC', 'Account has been banned and cannot login or perform any actions', 'en', 'enums', 'system'),

    -- AccountStatus Descriptions - Polish
    ('ACCOUNT_STATUS_INACTIVE_DESC', 'Konto jest nieaktywne i nie może być używane', 'pl', 'enums', 'system'),
    ('ACCOUNT_STATUS_IN_VALIDATION_DESC', 'Konto jest sprawdzane przez administratorów', 'pl', 'enums', 'system'),
    ('ACCOUNT_STATUS_ACTIVE_DESC', 'Konto jest aktywne i w pełni funkcjonalne', 'pl', 'enums', 'system'),
    ('ACCOUNT_STATUS_TO_BE_DELETED_DESC', 'Konto jest oznaczone do usunięcia', 'pl', 'enums', 'system'),
    ('ACCOUNT_STATUS_DELETED_DESC', 'Konto zostało trwale usunięte', 'pl', 'enums', 'system'),
    ('ACCOUNT_STATUS_BANNED_DESC', 'Konto zostało zbanowane i nie może się zalogować ani wykonywać żadnych działań', 'pl', 'enums', 'system'),

-- ============================================================================
-- ENUM TRANSLATIONS - CompensationType
-- ============================================================================

    -- CompensationType - English
    ('COMPENSATION_TYPE_CASH', 'Cash', 'en', 'enums', 'system'),
    ('COMPENSATION_TYPE_BARTER', 'Barter', 'en', 'enums', 'system'),

    -- CompensationType - Polish
    ('COMPENSATION_TYPE_CASH', 'Gotówka', 'pl', 'enums', 'system'),
    ('COMPENSATION_TYPE_BARTER', 'Barter', 'pl', 'enums', 'system'),

    -- CompensationType Descriptions - English
    ('COMPENSATION_TYPE_CASH_DESC', 'Payment in cash', 'en', 'enums', 'system'),
    ('COMPENSATION_TYPE_BARTER_DESC', 'Barter exchange', 'en', 'enums', 'system'),

    -- CompensationType Descriptions - Polish
    ('COMPENSATION_TYPE_CASH_DESC', 'Płatność gotówką', 'pl', 'enums', 'system'),
    ('COMPENSATION_TYPE_BARTER_DESC', 'Wymiana barterowa', 'pl', 'enums', 'system'),

-- ============================================================================
-- ENUM TRANSLATIONS - RateStatus
-- ============================================================================

    -- RateStatus - English
    ('RATE_STATUS_DEFAULT', 'Default', 'en', 'enums', 'system'),
    ('RATE_STATUS_POSITIVE', 'Positive', 'en', 'enums', 'system'),
    ('RATE_STATUS_NEGATIVE', 'Negative', 'en', 'enums', 'system'),

    -- RateStatus - Polish
    ('RATE_STATUS_DEFAULT', 'Domyślna', 'pl', 'enums', 'system'),
    ('RATE_STATUS_POSITIVE', 'Pozytywna', 'pl', 'enums', 'system'),
    ('RATE_STATUS_NEGATIVE', 'Negatywna', 'pl', 'enums', 'system'),

    -- RateStatus Descriptions - English
    ('RATE_STATUS_DEFAULT_DESC', 'Default rating', 'en', 'enums', 'system'),
    ('RATE_STATUS_POSITIVE_DESC', 'Positive rating', 'en', 'enums', 'system'),
    ('RATE_STATUS_NEGATIVE_DESC', 'Negative rating', 'en', 'enums', 'system'),

    -- RateStatus Descriptions - Polish
    ('RATE_STATUS_DEFAULT_DESC', 'Domyślna ocena', 'pl', 'enums', 'system'),
    ('RATE_STATUS_POSITIVE_DESC', 'Pozytywna ocena', 'pl', 'enums', 'system'),
    ('RATE_STATUS_NEGATIVE_DESC', 'Negatywna ocena', 'pl', 'enums', 'system'),

-- ============================================================================
-- CONSENT ACTION ENUM TRANSLATIONS
-- ============================================================================

    -- ConsentAction - English
    ('CONSENT_ACTION_GRANTED', 'Granted', 'en', 'enums', 'system'),
    ('CONSENT_ACTION_WITHDRAWN', 'Withdrawn', 'en', 'enums', 'system'),
    ('CONSENT_ACTION_UPDATED', 'Updated', 'en', 'enums', 'system'),

    -- ConsentAction - Polish
    ('CONSENT_ACTION_GRANTED', 'Udzielona', 'pl', 'enums', 'system'),
    ('CONSENT_ACTION_WITHDRAWN', 'Wycofana', 'pl', 'enums', 'system'),
    ('CONSENT_ACTION_UPDATED', 'Zaktualizowana', 'pl', 'enums', 'system'),

    -- ConsentAction Descriptions - English
    ('CONSENT_ACTION_GRANTED_DESC', 'User has granted consent for this specific purpose', 'en', 'enums', 'system'),
    ('CONSENT_ACTION_WITHDRAWN_DESC', 'User has withdrawn their previously given consent', 'en', 'enums', 'system'),
    ('CONSENT_ACTION_UPDATED_DESC', 'User has updated their consent preferences', 'en', 'enums', 'system'),

    -- ConsentAction Descriptions - Polish
    ('CONSENT_ACTION_GRANTED_DESC', 'Użytkownik udzielił zgody na ten konkretny cel', 'pl', 'enums', 'system'),
    ('CONSENT_ACTION_WITHDRAWN_DESC', 'Użytkownik wycofał swoją wcześniej udzieloną zgodę', 'pl', 'enums', 'system'),
    ('CONSENT_ACTION_UPDATED_DESC', 'Użytkownik zaktualizował swoje preferencje zgód', 'pl', 'enums', 'system'),

-- ============================================================================
-- CONSENT TYPES TRANSLATIONS
-- ============================================================================

    -- Consent Types - English
    ('CONSENT_TYPE_MARKETING', 'Marketing Communications', 'en', 'consent_types', 'system'),
    ('CONSENT_TYPE_ANALYTICS', 'Analytics and Performance', 'en', 'consent_types', 'system'),
    ('CONSENT_TYPE_COOKIES', 'Non-Essential Cookies', 'en', 'consent_types', 'system'),

    -- Consent Types - Polish
    ('CONSENT_TYPE_MARKETING', 'Komunikacja Marketingowa', 'pl', 'consent_types', 'system'),
    ('CONSENT_TYPE_ANALYTICS', 'Analityka i Wydajność', 'pl', 'consent_types', 'system'),
    ('CONSENT_TYPE_COOKIES', 'Nieobowiązkowe Pliki Cookie', 'pl', 'consent_types', 'system'),

    -- Consent Types Descriptions - English
    ('CONSENT_TYPE_MARKETING_DESC', 'Consent to receive marketing emails and promotional content', 'en', 'consent_types', 'system'),
    ('CONSENT_TYPE_ANALYTICS_DESC', 'Consent to collect analytics data to improve our services', 'en', 'consent_types', 'system'),
    ('CONSENT_TYPE_COOKIES_DESC', 'Consent to use non-essential cookies for enhanced user experience', 'en', 'consent_types', 'system'),

    -- Consent Types Descriptions - Polish
    ('CONSENT_TYPE_MARKETING_DESC', 'Zgoda na otrzymywanie e-maili marketingowych i treści promocyjnych', 'pl', 'consent_types', 'system'),
    ('CONSENT_TYPE_ANALYTICS_DESC', 'Zgoda na zbieranie danych analitycznych w celu poprawy naszych usług', 'pl', 'consent_types', 'system'),
    ('CONSENT_TYPE_COOKIES_DESC', 'Zgoda na używanie nieobowiązkowych plików cookie dla lepszego doświadczenia użytkownika', 'pl', 'consent_types', 'system'),

-- ============================================================================
-- COLLECTION METHODS TRANSLATIONS
-- ============================================================================

    -- Collection Methods - English
    ('COLLECTION_METHOD_WEB_FORM', 'Web Form', 'en', 'collection_methods', 'system'),
    ('COLLECTION_METHOD_API', 'API', 'en', 'collection_methods', 'system'),
    ('COLLECTION_METHOD_IMPORT', 'Data Import', 'en', 'collection_methods', 'system'),

    -- Collection Methods - Polish
    ('COLLECTION_METHOD_WEB_FORM', 'Formularz Internetowy', 'pl', 'collection_methods', 'system'),
    ('COLLECTION_METHOD_API', 'API', 'pl', 'collection_methods', 'system'),
    ('COLLECTION_METHOD_IMPORT', 'Import Danych', 'pl', 'collection_methods', 'system'),

    -- Collection Methods Descriptions - English
    ('COLLECTION_METHOD_WEB_FORM_DESC', 'Consent collected through web form interface', 'en', 'collection_methods', 'system'),
    ('COLLECTION_METHOD_API_DESC', 'Consent collected via API endpoint', 'en', 'collection_methods', 'system'),
    ('COLLECTION_METHOD_IMPORT_DESC', 'Consent imported from external system or bulk upload', 'en', 'collection_methods', 'system'),

    -- Collection Methods Descriptions - Polish
    ('COLLECTION_METHOD_WEB_FORM_DESC', 'Zgoda zebrana poprzez interfejs formularza internetowego', 'pl', 'collection_methods', 'system'),
    ('COLLECTION_METHOD_API_DESC', 'Zgoda zebrana przez punkt końcowy API', 'pl', 'collection_methods', 'system'),
    ('COLLECTION_METHOD_IMPORT_DESC', 'Zgoda zaimportowana z zewnętrznego systemu lub przesłania zbiorczego', 'pl', 'collection_methods', 'system'),

-- ============================================================================
-- LEGAL BASIS TRANSLATIONS
-- ============================================================================

    -- Legal Basis - English
    ('LEGAL_BASIS_CONSENT', 'Consent', 'en', 'legal_basis', 'system'),
    ('LEGAL_BASIS_LEGITIMATE_INTEREST', 'Legitimate Interest', 'en', 'legal_basis', 'system'),

    -- Legal Basis - Polish
    ('LEGAL_BASIS_CONSENT', 'Zgoda', 'pl', 'legal_basis', 'system'),
    ('LEGAL_BASIS_LEGITIMATE_INTEREST', 'Uzasadniony Interes', 'pl', 'legal_basis', 'system'),

    -- Legal Basis Descriptions - English
    ('LEGAL_BASIS_CONSENT_DESC', 'Processing based on freely given consent under GDPR Article 6(1)(a)', 'en', 'legal_basis', 'system'),
    ('LEGAL_BASIS_LEGITIMATE_INTEREST_DESC', 'Processing based on legitimate interests under GDPR Article 6(1)(f)', 'en', 'legal_basis', 'system'),

    -- Legal Basis Descriptions - Polish
    ('LEGAL_BASIS_CONSENT_DESC', 'Przetwarzanie oparte na dobrowolnie udzielonej zgodzie zgodnie z art. 6 ust. 1 lit. a RODO', 'pl', 'legal_basis', 'system'),
    ('LEGAL_BASIS_LEGITIMATE_INTEREST_DESC', 'Przetwarzanie oparte na uzasadnionych interesach zgodnie z art. 6 ust. 1 lit. f RODO', 'pl', 'legal_basis', 'system'),

-- ============================================================================
-- CONSENT UI/UX TRANSLATIONS
-- ============================================================================

    -- UI Labels - English
    ('CONSENT_LABEL_ACCEPT_ALL', 'Accept All', 'en', 'consent_ui', 'system'),
    ('CONSENT_LABEL_REJECT_ALL', 'Reject All', 'en', 'consent_ui', 'system'),
    ('CONSENT_LABEL_SAVE_PREFERENCES', 'Save Preferences', 'en', 'consent_ui', 'system'),
    ('CONSENT_LABEL_MANAGE_CONSENT', 'Manage Consent', 'en', 'consent_ui', 'system'),
    ('CONSENT_LABEL_CONSENT_SETTINGS', 'Consent Settings', 'en', 'consent_ui', 'system'),
    ('CONSENT_LABEL_PRIVACY_POLICY', 'Privacy Policy', 'en', 'consent_ui', 'system'),
    ('CONSENT_LABEL_REQUIRED', 'Required', 'en', 'consent_ui', 'system'),
    ('CONSENT_LABEL_OPTIONAL', 'Optional', 'en', 'consent_ui', 'system'),

    -- UI Labels - Polish
    ('CONSENT_LABEL_ACCEPT_ALL', 'Zaakceptuj Wszystko', 'pl', 'consent_ui', 'system'),
    ('CONSENT_LABEL_REJECT_ALL', 'Odrzuć Wszystko', 'pl', 'consent_ui', 'system'),
    ('CONSENT_LABEL_SAVE_PREFERENCES', 'Zapisz Preferencje', 'pl', 'consent_ui', 'system'),
    ('CONSENT_LABEL_MANAGE_CONSENT', 'Zarządzaj Zgodami', 'pl', 'consent_ui', 'system'),
    ('CONSENT_LABEL_CONSENT_SETTINGS', 'Ustawienia Zgód', 'pl', 'consent_ui', 'system'),
    ('CONSENT_LABEL_PRIVACY_POLICY', 'Polityka Prywatności', 'pl', 'consent_ui', 'system'),
    ('CONSENT_LABEL_REQUIRED', 'Wymagane', 'pl', 'consent_ui', 'system'),
    ('CONSENT_LABEL_OPTIONAL', 'Opcjonalne', 'pl', 'consent_ui', 'system'),

    -- Messages - English
    ('CONSENT_MESSAGE_SAVED', 'Your consent preferences have been saved successfully', 'en', 'consent_messages', 'system'),
    ('CONSENT_MESSAGE_UPDATED', 'Your consent preferences have been updated', 'en', 'consent_messages', 'system'),
    ('CONSENT_MESSAGE_WITHDRAWN', 'Your consent has been withdrawn successfully', 'en', 'consent_messages', 'system'),
    ('CONSENT_MESSAGE_REQUIRED_MISSING', 'Please provide consent for required items', 'en', 'consent_messages', 'system'),

    -- Messages - Polish
    ('CONSENT_MESSAGE_SAVED', 'Twoje preferencje zgód zostały pomyślnie zapisane', 'pl', 'consent_messages', 'system'),
    ('CONSENT_MESSAGE_UPDATED', 'Twoje preferencje zgód zostały zaktualizowane', 'pl', 'consent_messages', 'system'),
    ('CONSENT_MESSAGE_WITHDRAWN', 'Twoja zgoda została pomyślnie wycofana', 'pl', 'consent_messages', 'system'),
    ('CONSENT_MESSAGE_REQUIRED_MISSING', 'Proszę udzielić zgody na wymagane elementy', 'pl', 'consent_messages', 'system'),

-- ============================================================================
-- CONSENT VERSION STATUS TRANSLATIONS
-- ============================================================================

    -- Consent Version Status - English
    ('CONSENT_STATUS_ACTIVE', 'Active', 'en', 'consent_status', 'system'),
    ('CONSENT_STATUS_INACTIVE', 'Inactive', 'en', 'consent_status', 'system'),
    ('CONSENT_STATUS_PENDING', 'Pending', 'en', 'consent_status', 'system'),
    ('CONSENT_STATUS_EXPIRED', 'Expired', 'en', 'consent_status', 'system'),

    -- Consent Version Status - Polish
    ('CONSENT_STATUS_ACTIVE', 'Aktywna', 'pl', 'consent_status', 'system'),
    ('CONSENT_STATUS_INACTIVE', 'Nieaktywna', 'pl', 'consent_status', 'system'),
    ('CONSENT_STATUS_PENDING', 'Oczekująca', 'pl', 'consent_status', 'system'),
    ('CONSENT_STATUS_EXPIRED', 'Wygasła', 'pl', 'consent_status', 'system'),

    -- Consent Version Status Descriptions - English
    ('CONSENT_STATUS_ACTIVE_DESC', 'This consent version is currently active and in use', 'en', 'consent_status', 'system'),
    ('CONSENT_STATUS_INACTIVE_DESC', 'This consent version is not currently active', 'en', 'consent_status', 'system'),
    ('CONSENT_STATUS_PENDING_DESC', 'This consent version is pending activation', 'en', 'consent_status', 'system'),
    ('CONSENT_STATUS_EXPIRED_DESC', 'This consent version has expired and is no longer valid', 'en', 'consent_status', 'system'),

    -- Consent Version Status Descriptions - Polish
    ('CONSENT_STATUS_ACTIVE_DESC', 'Ta wersja zgody jest obecnie aktywna i używana', 'pl', 'consent_status', 'system'),
    ('CONSENT_STATUS_INACTIVE_DESC', 'Ta wersja zgody nie jest obecnie aktywna', 'pl', 'consent_status', 'system'),
    ('CONSENT_STATUS_PENDING_DESC', 'Ta wersja zgody oczekuje na aktywację', 'pl', 'consent_status', 'system'),
    ('CONSENT_STATUS_EXPIRED_DESC', 'Ta wersja zgody wygasła i nie jest już ważna', 'pl', 'consent_status', 'system'),

-- ============================================================================
-- DELETION BLOCKER CATEGORY TRANSLATIONS - English
-- ============================================================================

    -- Main labels - English
    ('DELETION_BLOCKER_ACTIVE_OPPORTUNITIES', 'Active Collaborations', 'en', 'deletion_blockers', 'system'),
    ('DELETION_BLOCKER_PENDING_OPPORTUNITIES', 'Pending Applications', 'en', 'deletion_blockers', 'system'),
    ('DELETION_BLOCKER_ACTIVE_PARTNERSHIP_OPPORTUNITIES', 'Active Campaigns', 'en', 'deletion_blockers', 'system'),
    ('DELETION_BLOCKER_OPPORTUNITIES_WITH_APPLICATIONS', 'Campaigns with Applications', 'en', 'deletion_blockers', 'system'),
    ('DELETION_BLOCKER_LAST_ADMIN', 'Last Administrator', 'en', 'deletion_blockers', 'system'),
    ('DELETION_BLOCKER_OPEN_SUPPORT_TICKETS', 'Open Support Tickets', 'en', 'deletion_blockers', 'system'),
    ('DELETION_BLOCKER_SUPPORT_TICKETS_HISTORY', 'Support Ticket History', 'en', 'deletion_blockers', 'system'),
    ('DELETION_BLOCKER_RECENT_ACTIVITY', 'Recent Activity', 'en', 'deletion_blockers', 'system'),
    ('DELETION_BLOCKER_DATA_RETENTION_REQUIRED', 'Data Retention Required', 'en', 'deletion_blockers', 'system'),

    -- Descriptions - English
    ('DELETION_BLOCKER_ACTIVE_OPPORTUNITIES_DESC', 'You have active collaborations that must be completed or cancelled before account deletion', 'en', 'deletion_blockers', 'system'),
    ('DELETION_BLOCKER_PENDING_OPPORTUNITIES_DESC', 'You have pending campaign applications. Consider withdrawing them before deletion', 'en', 'deletion_blockers', 'system'),
    ('DELETION_BLOCKER_ACTIVE_PARTNERSHIP_OPPORTUNITIES_DESC', 'Your company has active campaigns. Please close or complete them before deletion', 'en', 'deletion_blockers', 'system'),
    ('DELETION_BLOCKER_OPPORTUNITIES_WITH_APPLICATIONS_DESC', 'Some of your campaigns have influencer applications that need to be resolved', 'en', 'deletion_blockers', 'system'),
    ('DELETION_BLOCKER_LAST_ADMIN_DESC', 'Cannot delete the last admin account. Promote another user to admin first', 'en', 'deletion_blockers', 'system'),
    ('DELETION_BLOCKER_OPEN_SUPPORT_TICKETS_DESC', 'You have open support tickets that need to be resolved first', 'en', 'deletion_blockers', 'system'),
    ('DELETION_BLOCKER_SUPPORT_TICKETS_HISTORY_DESC', 'User has support ticket history that may need to be retained for audit purposes', 'en', 'deletion_blockers', 'system'),
    ('DELETION_BLOCKER_RECENT_ACTIVITY_DESC', 'User has recent activity within the last 30 days. Consider waiting for account to be inactive longer', 'en', 'deletion_blockers', 'system'),
    ('DELETION_BLOCKER_DATA_RETENTION_REQUIRED_DESC', 'Legal or regulatory requirements prevent immediate deletion of this user''s data', 'en', 'deletion_blockers', 'system'),

-- ============================================================================
-- DELETION BLOCKER CATEGORY TRANSLATIONS - Polish
-- ============================================================================

    -- Main labels - Polish
    ('DELETION_BLOCKER_ACTIVE_OPPORTUNITIES', 'Aktywne Współprace', 'pl', 'deletion_blockers', 'system'),
    ('DELETION_BLOCKER_PENDING_OPPORTUNITIES', 'Oczekujące Aplikacje', 'pl', 'deletion_blockers', 'system'),
    ('DELETION_BLOCKER_ACTIVE_PARTNERSHIP_OPPORTUNITIES', 'Aktywne Kampanie', 'pl', 'deletion_blockers', 'system'),
    ('DELETION_BLOCKER_OPPORTUNITIES_WITH_APPLICATIONS', 'Kampanie z Aplikacjami', 'pl', 'deletion_blockers', 'system'),
    ('DELETION_BLOCKER_LAST_ADMIN', 'Ostatni Administrator', 'pl', 'deletion_blockers', 'system'),
    ('DELETION_BLOCKER_OPEN_SUPPORT_TICKETS', 'Otwarte Zgłoszenia', 'pl', 'deletion_blockers', 'system'),
    ('DELETION_BLOCKER_SUPPORT_TICKETS_HISTORY', 'Historia Zgłoszeń', 'pl', 'deletion_blockers', 'system'),
    ('DELETION_BLOCKER_RECENT_ACTIVITY', 'Ostatnia Aktywność', 'pl', 'deletion_blockers', 'system'),
    ('DELETION_BLOCKER_DATA_RETENTION_REQUIRED', 'Wymagane Przechowywanie Danych', 'pl', 'deletion_blockers', 'system'),

    -- Descriptions - Polish
    ('DELETION_BLOCKER_ACTIVE_OPPORTUNITIES_DESC', 'Masz aktywne współprace, które muszą zostać ukończone lub anulowane przed usunięciem konta', 'pl', 'deletion_blockers', 'system'),
    ('DELETION_BLOCKER_PENDING_OPPORTUNITIES_DESC', 'Masz oczekujące aplikacje do kampanii. Rozważ ich wycofanie przed usunięciem', 'pl', 'deletion_blockers', 'system'),
    ('DELETION_BLOCKER_ACTIVE_PARTNERSHIP_OPPORTUNITIES_DESC', 'Twoja firma ma aktywne kampanie. Zamknij lub ukończ je przed usunięciem', 'pl', 'deletion_blockers', 'system'),
    ('DELETION_BLOCKER_OPPORTUNITIES_WITH_APPLICATIONS_DESC', 'Niektóre z Twoich kampanii mają aplikacje influencerów, które wymagają rozwiązania', 'pl', 'deletion_blockers', 'system'),
    ('DELETION_BLOCKER_LAST_ADMIN_DESC', 'Nie można usunąć ostatniego konta administratora. Najpierw promuj innego użytkownika na administratora', 'pl', 'deletion_blockers', 'system'),
    ('DELETION_BLOCKER_OPEN_SUPPORT_TICKETS_DESC', 'Masz otwarte zgłoszenia, które muszą zostać rozwiązane najpierw', 'pl', 'deletion_blockers', 'system'),
    ('DELETION_BLOCKER_SUPPORT_TICKETS_HISTORY_DESC', 'Użytkownik ma historię zgłoszeń, która może wymagać przechowywania do celów audytu', 'pl', 'deletion_blockers', 'system'),
    ('DELETION_BLOCKER_RECENT_ACTIVITY_DESC', 'Użytkownik miał ostatnią aktywność w ciągu ostatnich 30 dni. Rozważ oczekiwanie na dłuższą nieaktywność konta', 'pl', 'deletion_blockers', 'system'),
    ('DELETION_BLOCKER_DATA_RETENTION_REQUIRED_DESC', 'Wymagania prawne lub regulacyjne uniemożliwiają natychmiastowe usunięcie danych tego użytkownika', 'pl', 'deletion_blockers', 'system'),

-- ============================================================================
-- DELETION ELIGIBILITY SUMMARY TRANSLATIONS
-- ============================================================================

    -- Summary messages - English
    ('DELETION_SUMMARY_CAN_DELETE_ALL', 'Can be both soft deleted and permanently deleted', 'en', 'deletion_summaries', 'system'),
    ('DELETION_SUMMARY_CAN_SOFT_DELETE_ONLY', 'Can be soft deleted but not permanently deleted', 'en', 'deletion_summaries', 'system'),
    ('DELETION_SUMMARY_CANNOT_DELETE', 'Cannot be deleted', 'en', 'deletion_summaries', 'system'),
    ('DELETION_SUMMARY_BLOCKERS_COUNT', 'blocker(s)', 'en', 'deletion_summaries', 'system'),
    ('DELETION_SUMMARY_PERMANENT_BLOCKERS_COUNT', 'blocker(s) for permanent deletion', 'en', 'deletion_summaries', 'system'),

    -- Summary messages - Polish
    ('DELETION_SUMMARY_CAN_DELETE_ALL', 'Można usunąć zarówno miękko jak i na stałe', 'pl', 'deletion_summaries', 'system'),
    ('DELETION_SUMMARY_CAN_SOFT_DELETE_ONLY', 'Można usunąć miękko, ale nie na stałe', 'pl', 'deletion_summaries', 'system'),
    ('DELETION_SUMMARY_CANNOT_DELETE', 'Nie można usunąć', 'pl', 'deletion_summaries', 'system'),
    ('DELETION_SUMMARY_BLOCKERS_COUNT', 'blokada(y)', 'pl', 'deletion_summaries', 'system'),
    ('DELETION_SUMMARY_PERMANENT_BLOCKERS_COUNT', 'blokada(y) dla stałego usunięcia', 'pl', 'deletion_summaries', 'system');

-- rollback DELETE FROM dictionary_entries WHERE updater_id = 'system' AND category IN ('service_types', 'service_categories', 'currencies', 'content_types', 'enums', 'consent_types', 'collection_methods', 'legal_basis', 'consent_ui', 'consent_messages', 'consent_status', 'deletion_blockers', 'deletion_summaries');
