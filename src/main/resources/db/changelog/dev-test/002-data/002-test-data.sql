-- liquibase formatted sql
-- changeset system:002-test-data context:dev,test,prod

-- ============================================================================
-- TEST DATA - Using correct column names from schema
-- ============================================================================

-- Create test admin
WITH test_admin AS (
    INSERT INTO "user" (firebase_user_id, user_type, email, name, account_status)
    VALUES ('TEST_ADMIN_001', 'ADMIN', 'test.admin@test.com', 'Test Admin', 'ACTIVE')
    ON CONFLICT (email) DO UPDATE SET 
        firebase_user_id = EXCLUDED.firebase_user_id,
        account_status = EXCLUDED.account_status
    RETURNING id
)
SELECT id FROM test_admin;

-- Create test influencer 1
WITH test_influencer1 AS (
    INSERT INTO "user" (firebase_user_id, user_type, email, name, account_status)
    VALUES ('cDKyirInmXNwVkBCD5LJmd7A4ak1', 'INFLUENCER', 'test.influencer@test.com', 'Test Influencer', 'ACTIVE')
    ON CONFLICT (email) DO UPDATE SET
        firebase_user_id = EXCLUDED.firebase_user_id,
        account_status = EXCLUDED.account_status
    RETURNING id
)
SELECT id FROM test_influencer1;

-- Create test influencer 2
WITH test_influencer2 AS (
    INSERT INTO "user" (firebase_user_id, user_type, email, name, account_status)
    VALUES ('4tZkhv0j9KWKI2GquYs2at2aQvT2', 'INFLUENCER', 'test.influencer2@test.com', 'Test Influencer 2', 'ACTIVE')
    ON CONFLICT (email) DO UPDATE SET
        firebase_user_id = EXCLUDED.firebase_user_id,
        account_status = EXCLUDED.account_status
    RETURNING id
)
SELECT id FROM test_influencer2;

-- Create test company 1
WITH test_company1 AS (
    INSERT INTO "user" (firebase_user_id, user_type, email, name, account_status, company_description, nip)
    VALUES ('ZHXlEDREjKYHD0hOmi8ILm724su1', 'COMPANY', 'test.company@test.com', 'Test Company', 'ACTIVE', 'Test Company Description', '1234567890')
    ON CONFLICT (email) DO UPDATE SET 
        firebase_user_id = EXCLUDED.firebase_user_id,
        account_status = EXCLUDED.account_status,
        company_description = EXCLUDED.company_description,
        nip = EXCLUDED.nip
    RETURNING id
)
SELECT id FROM test_company1;

-- Create test company 2  
WITH test_company2 AS (
    INSERT INTO "user" (firebase_user_id, user_type, email, name, account_status, company_description, nip)
    VALUES ('uVOlWYYmOvQsbq703Lo7BWeg3P23', 'COMPANY', 'test.company2@test.com', 'Test Company 2', 'ACTIVE', 'Test Company 2 Description', '0987654321')
    ON CONFLICT (email) DO UPDATE SET
        firebase_user_id = EXCLUDED.firebase_user_id,
        account_status = EXCLUDED.account_status,
        company_description = EXCLUDED.company_description,
        nip = EXCLUDED.nip
    RETURNING id
)
SELECT id FROM test_company2;

-- Create partnership opportunities with correct column names
INSERT INTO partnership_opportunity (name, company_id, title, city_id, service_id, currency_id, details, compensation_type, compensation_amount_min, compensation_amount_max)
SELECT 
    'Test Restaurant Partnership',
    u.id,
    'Restaurant Collaboration',
    c.id,
    s.id,
    cur.id,
    'Looking for food influencers',
    'CASH',
    500,
    500
FROM "user" u
CROSS JOIN city c
CROSS JOIN service_type s
CROSS JOIN currency cur
WHERE u.firebase_user_id = 'ZHXlEDREjKYHD0hOmi8ILm724su1'
  AND c.name = 'Warszawa'
  AND s.name = 'Restauracja'
  AND cur.iso_code = 'PLN'
  AND NOT EXISTS (
    SELECT 1 FROM partnership_opportunity 
    WHERE name = 'Test Restaurant Partnership'
  );

INSERT INTO partnership_opportunity (name, company_id, title, city_id, service_id, currency_id, details, compensation_type, compensation_amount_min, compensation_amount_max)
SELECT 
    'Test Fashion Partnership',
    u.id,
    'Fashion Brand Deal',
    c.id,
    s.id,
    cur.id,
    'Fashion influencer needed',
    'BARTER',
    0,
    0
FROM "user" u
CROSS JOIN city c
CROSS JOIN service_type s
CROSS JOIN currency cur
WHERE u.firebase_user_id = 'uVOlWYYmOvQsbq703Lo7BWeg3P23'
  AND c.name = 'Kraków'
  AND s.name = 'Sklep odzieżowy'
  AND cur.iso_code = 'EUR'
  AND NOT EXISTS (
    SELECT 1 FROM partnership_opportunity 
    WHERE name = 'Test Fashion Partnership'
  );

INSERT INTO partnership_opportunity (name, company_id, title, city_id, service_id, currency_id, details, compensation_type, compensation_amount_min, compensation_amount_max)
SELECT 
    'Test Tech Partnership',
    u.id,
    'Tech Review Opportunity',
    c.id,
    s.id,
    cur.id,
    'Tech reviewer wanted',
    'CASH',
    1000,
    1000
FROM "user" u
CROSS JOIN city c
CROSS JOIN service_type s
CROSS JOIN currency cur
WHERE u.firebase_user_id = 'ZHXlEDREjKYHD0hOmi8ILm724su1'
  AND c.name = 'Warszawa'
  AND s.name = 'Sklep internetowy'
  AND cur.iso_code = 'USD'
  AND NOT EXISTS (
    SELECT 1 FROM partnership_opportunity 
    WHERE name = 'Test Tech Partnership'
  );

-- Create additional partnership opportunities to cover all statuses
INSERT INTO partnership_opportunity (name, company_id, title, city_id, service_id, currency_id, details, compensation_type, compensation_amount_min, compensation_amount_max)
SELECT 
    'Test Fitness Partnership',
    u.id,
    'Fitness Brand Collaboration',
    c.id,
    s.id,
    cur.id,
    'Looking for fitness influencers for gym promotion',
    'CASH',
    750,
    750
FROM "user" u
CROSS JOIN city c
CROSS JOIN service_type s
CROSS JOIN currency cur
WHERE u.firebase_user_id = 'uVOlWYYmOvQsbq703Lo7BWeg3P23'
  AND c.name = 'Trójmiasto (Gdańsk, Gdynia, Sopot)'
  AND s.name = 'Siłownia i fitness'
  AND cur.iso_code = 'PLN'
  AND NOT EXISTS (
    SELECT 1 FROM partnership_opportunity 
    WHERE name = 'Test Fitness Partnership'
  );

INSERT INTO partnership_opportunity (name, company_id, title, city_id, service_id, currency_id, details, compensation_type, compensation_amount_min, compensation_amount_max)
SELECT 
    'Test Beauty Partnership',
    u.id,
    'Beauty Product Showcase',
    c.id,
    s.id,
    cur.id,
    'Beauty influencer needed for new product line',
    'BARTER',
    0,
    0
FROM "user" u
CROSS JOIN city c
CROSS JOIN service_type s
CROSS JOIN currency cur
WHERE u.firebase_user_id = 'ZHXlEDREjKYHD0hOmi8ILm724su1'
  AND c.name = 'Warszawa'
  AND s.name = 'Sklep z kosmetykami'
  AND cur.iso_code = 'PLN'
  AND NOT EXISTS (
    SELECT 1 FROM partnership_opportunity 
    WHERE name = 'Test Beauty Partnership'
  );

INSERT INTO partnership_opportunity (name, company_id, title, city_id, service_id, currency_id, details, compensation_type, compensation_amount_min, compensation_amount_max)
SELECT 
    'Test Travel Partnership',
    u.id,
    'Travel Content Creation',
    c.id,
    s.id,
    cur.id,
    'Travel influencer for destination promotion',
    'CASH',
    1200,
    1500
FROM "user" u
CROSS JOIN city c
CROSS JOIN service_type s
CROSS JOIN currency cur
WHERE u.firebase_user_id = 'uVOlWYYmOvQsbq703Lo7BWeg3P23'
  AND c.name = 'Kraków'
  AND s.name = 'Hotel i spa'
  AND cur.iso_code = 'EUR'
  AND NOT EXISTS (
    SELECT 1 FROM partnership_opportunity 
    WHERE name = 'Test Travel Partnership'
  );

INSERT INTO partnership_opportunity (name, company_id, title, city_id, service_id, currency_id, details, compensation_type, compensation_amount_min, compensation_amount_max)
SELECT 
    'Test Gaming Partnership',
    u.id,
    'Gaming Product Review',
    c.id,
    s.id,
    cur.id,
    'Gaming influencer for new product launch',
    'CASH',
    800,
    1200
FROM "user" u
CROSS JOIN city c
CROSS JOIN service_type s
CROSS JOIN currency cur
WHERE u.firebase_user_id = 'ZHXlEDREjKYHD0hOmi8ILm724su1'
  AND c.name = 'Wrocław'
  AND s.name = 'Sklep internetowy'
  AND cur.iso_code = 'USD'
  AND NOT EXISTS (
    SELECT 1 FROM partnership_opportunity 
    WHERE name = 'Test Gaming Partnership'
  );

-- ============================================================================
-- APPLIED OPPORTUNITIES - Covering ALL OpportunityStatus values
-- Note: Each user can apply only once per partnership opportunity
-- ============================================================================

-- 1. APPLIED Status
INSERT INTO applied_opportunity (partnership_opportunity_id, influencer_id, note, opportunity_status, rate_status, company_rate_status)
SELECT 
    po.id,
    u.id,
    'TEST: I am interested in this restaurant partnership',
    'APPLIED',
    'DEFAULT',
    'DEFAULT'
FROM partnership_opportunity po
CROSS JOIN "user" u
WHERE po.name = 'Test Restaurant Partnership'
  AND u.firebase_user_id = 'cDKyirInmXNwVkBCD5LJmd7A4ak1'
  AND NOT EXISTS (
    SELECT 1 FROM applied_opportunity ao
    WHERE ao.partnership_opportunity_id = po.id AND ao.influencer_id = u.id
  );

-- 2. ACCEPTED_BY_COMPANY Status
INSERT INTO applied_opportunity (partnership_opportunity_id, influencer_id, note, opportunity_status, rate_status, company_rate_status)
SELECT 
    po.id,
    u.id,
    'TEST: Would love to collaborate on fashion content',
    'ACCEPTED_BY_COMPANY',
    'DEFAULT',
    'DEFAULT'
FROM partnership_opportunity po
CROSS JOIN "user" u
WHERE po.name = 'Test Fashion Partnership'
  AND u.firebase_user_id = 'cDKyirInmXNwVkBCD5LJmd7A4ak1'
  AND NOT EXISTS (
    SELECT 1 FROM applied_opportunity ao
    WHERE ao.partnership_opportunity_id = po.id AND ao.influencer_id = u.id
  );

-- 3. REJECTED_BY_COMPANY Status
INSERT INTO applied_opportunity (partnership_opportunity_id, influencer_id, note, opportunity_status, rate_status, company_rate_status)
SELECT 
    po.id,
    u.id,
    'TEST: Interested in tech review opportunity',
    'REJECTED_BY_COMPANY',
    'NEGATIVE',
    'DEFAULT'
FROM partnership_opportunity po
CROSS JOIN "user" u
WHERE po.name = 'Test Tech Partnership'
  AND u.firebase_user_id = '4tZkhv0j9KWKI2GquYs2at2aQvT2'
  AND NOT EXISTS (
    SELECT 1 FROM applied_opportunity ao
    WHERE ao.partnership_opportunity_id = po.id AND ao.influencer_id = u.id
  );

-- 4. ACCEPTED_BY_INFLUENCER Status
INSERT INTO applied_opportunity (partnership_opportunity_id, influencer_id, note, opportunity_status, rate_status, company_rate_status)
SELECT 
    po.id,
    u.id,
    'TEST: Excited about this fitness collaboration',
    'ACCEPTED_BY_INFLUENCER',
    'DEFAULT',
    'POSITIVE'
FROM partnership_opportunity po
CROSS JOIN "user" u
WHERE po.name = 'Test Fitness Partnership'
  AND u.firebase_user_id = 'cDKyirInmXNwVkBCD5LJmd7A4ak1'
  AND NOT EXISTS (
    SELECT 1 FROM applied_opportunity ao
    WHERE ao.partnership_opportunity_id = po.id AND ao.influencer_id = u.id
  );

-- 5. REJECTED_BY_INFLUENCER Status
INSERT INTO applied_opportunity (partnership_opportunity_id, influencer_id, note, opportunity_status, rate_status, company_rate_status)
SELECT 
    po.id,
    u.id,
    'TEST: Applied but changed my mind about beauty partnership',
    'REJECTED_BY_INFLUENCER',
    'DEFAULT',
    'NEGATIVE'
FROM partnership_opportunity po
CROSS JOIN "user" u
WHERE po.name = 'Test Beauty Partnership'
  AND u.firebase_user_id = '4tZkhv0j9KWKI2GquYs2at2aQvT2'
  AND NOT EXISTS (
    SELECT 1 FROM applied_opportunity ao
    WHERE ao.partnership_opportunity_id = po.id AND ao.influencer_id = u.id
  );

-- 6. CONTENT_SEND_TO_ACCEPT Status
INSERT INTO applied_opportunity (partnership_opportunity_id, influencer_id, note, opportunity_status, rate_status, company_rate_status)
SELECT 
    po.id,
    u.id,
    'TEST: Submitted travel content for review',
    'CONTENT_SEND_TO_ACCEPT',
    'DEFAULT',
    'DEFAULT'
FROM partnership_opportunity po
CROSS JOIN "user" u
WHERE po.name = 'Test Travel Partnership'
  AND u.firebase_user_id = '4tZkhv0j9KWKI2GquYs2at2aQvT2'
  AND NOT EXISTS (
    SELECT 1 FROM applied_opportunity ao
    WHERE ao.partnership_opportunity_id = po.id AND ao.influencer_id = u.id
  );

-- 7. CONTENT_APPROVED Status
INSERT INTO applied_opportunity (partnership_opportunity_id, influencer_id, note, opportunity_status, rate_status, company_rate_status)
SELECT 
    po.id,
    u.id,
    'TEST: Gaming content approved, ready to post',
    'CONTENT_APPROVED',
    'DEFAULT',
    'POSITIVE'
FROM partnership_opportunity po
CROSS JOIN "user" u
WHERE po.name = 'Test Gaming Partnership'
  AND u.firebase_user_id = 'cDKyirInmXNwVkBCD5LJmd7A4ak1'
  AND NOT EXISTS (
    SELECT 1 FROM applied_opportunity ao
    WHERE ao.partnership_opportunity_id = po.id AND ao.influencer_id = u.id
  );

-- 8. CONTENT_REJECTED Status
INSERT INTO applied_opportunity (partnership_opportunity_id, influencer_id, note, opportunity_status, rate_status, company_rate_status)
SELECT 
    po.id,
    u.id,
    'TEST: Content needs revision for restaurant',
    'CONTENT_REJECTED',
    'DEFAULT',
    'DEFAULT'
FROM partnership_opportunity po
CROSS JOIN "user" u
WHERE po.name = 'Test Restaurant Partnership'
  AND u.firebase_user_id = '4tZkhv0j9KWKI2GquYs2at2aQvT2'
  AND NOT EXISTS (
    SELECT 1 FROM applied_opportunity ao
    WHERE ao.partnership_opportunity_id = po.id AND ao.influencer_id = u.id
  );

-- 9. CONTENT_POSTED Status
INSERT INTO applied_opportunity (partnership_opportunity_id, influencer_id, note, opportunity_status, rate_status, company_rate_status)
SELECT 
    po.id,
    u.id,
    'TEST: Fashion content posted successfully',
    'CONTENT_POSTED',
    'POSITIVE',
    'POSITIVE'
FROM partnership_opportunity po
CROSS JOIN "user" u
WHERE po.name = 'Test Fashion Partnership'
  AND u.firebase_user_id = '4tZkhv0j9KWKI2GquYs2at2aQvT2'
  AND NOT EXISTS (
    SELECT 1 FROM applied_opportunity ao
    WHERE ao.partnership_opportunity_id = po.id AND ao.influencer_id = u.id
  );

-- 10. CONTENT_POSTED_REJECTED Status
INSERT INTO applied_opportunity (partnership_opportunity_id, influencer_id, note, opportunity_status, rate_status, company_rate_status)
SELECT 
    po.id,
    u.id,
    'TEST: Posted content but needs correction',
    'CONTENT_POSTED_REJECTED',
    'DEFAULT',
    'DEFAULT'
FROM partnership_opportunity po
CROSS JOIN "user" u
WHERE po.name = 'Test Tech Partnership'
  AND u.firebase_user_id = 'cDKyirInmXNwVkBCD5LJmd7A4ak1'
  AND NOT EXISTS (
    SELECT 1 FROM applied_opportunity ao
    WHERE ao.partnership_opportunity_id = po.id AND ao.influencer_id = u.id
  );

-- 11. TO_BE_PAID Status
INSERT INTO applied_opportunity (partnership_opportunity_id, influencer_id, note, opportunity_status, rate_status, company_rate_status)
SELECT 
    po.id,
    u.id,
    'TEST: Fitness content completed, awaiting payment',
    'TO_BE_PAID',
    'POSITIVE',
    'POSITIVE'
FROM partnership_opportunity po
CROSS JOIN "user" u
WHERE po.name = 'Test Fitness Partnership'
  AND u.firebase_user_id = '4tZkhv0j9KWKI2GquYs2at2aQvT2'
  AND NOT EXISTS (
    SELECT 1 FROM applied_opportunity ao
    WHERE ao.partnership_opportunity_id = po.id AND ao.influencer_id = u.id
  );

-- 12. DONE Status (Successful completion with ratings)
INSERT INTO applied_opportunity (partnership_opportunity_id, influencer_id, note, opportunity_status, rate_status, company_rate_status)
SELECT 
    po.id,
    u.id,
    'TEST: Beauty collaboration completed successfully',
    'DONE',
    'POSITIVE',
    'POSITIVE'
FROM partnership_opportunity po
CROSS JOIN "user" u
WHERE po.name = 'Test Beauty Partnership'
  AND u.firebase_user_id = 'cDKyirInmXNwVkBCD5LJmd7A4ak1'
  AND NOT EXISTS (
    SELECT 1 FROM applied_opportunity ao
    WHERE ao.partnership_opportunity_id = po.id AND ao.influencer_id = u.id
  );

-- Additional Applied Opportunities for cross-referencing and edge cases
-- Travel Partnership - Applied by second influencer
INSERT INTO applied_opportunity (partnership_opportunity_id, influencer_id, note, opportunity_status, rate_status, company_rate_status)
SELECT 
    po.id,
    u.id,
    'TEST: Love to create travel content',
    'APPLIED',
    'DEFAULT',
    'DEFAULT'
FROM partnership_opportunity po
CROSS JOIN "user" u
WHERE po.name = 'Test Travel Partnership'
  AND u.firebase_user_id = 'cDKyirInmXNwVkBCD5LJmd7A4ak1'
  AND NOT EXISTS (
    SELECT 1 FROM applied_opportunity ao
    WHERE ao.partnership_opportunity_id = po.id AND ao.influencer_id = u.id
  );

-- Gaming Partnership - Applied by second influencer  
INSERT INTO applied_opportunity (partnership_opportunity_id, influencer_id, note, opportunity_status, rate_status, company_rate_status)
SELECT 
    po.id,
    u.id,
    'TEST: Gaming enthusiast ready for collaboration',
    'APPLIED',
    'DEFAULT', 
    'DEFAULT'
FROM partnership_opportunity po
CROSS JOIN "user" u
WHERE po.name = 'Test Gaming Partnership'
  AND u.firebase_user_id = '4tZkhv0j9KWKI2GquYs2at2aQvT2'
  AND NOT EXISTS (
    SELECT 1 FROM applied_opportunity ao
    WHERE ao.partnership_opportunity_id = po.id AND ao.influencer_id = u.id
  );

-- ============================================================================
-- ADDRESSES - Add addresses for test users
-- ============================================================================

-- Address for Test Influencer 1
INSERT INTO address (user_id, street, city, postal_code, country, state, additional_info, address_type, is_primary, source_type, is_shared, reference_count, is_copied)
SELECT 
    u.id,
    'ul. Marszałkowska 123/45',
    'Warszawa',
    '00-001',
    'Polska',
    'Mazowieckie',
    'Apartament na 12 piętrze',
    'MAIN',
    true,
    'CUSTOM',
    false,
    1,
    false
FROM "user" u
WHERE u.firebase_user_id = 'cDKyirInmXNwVkBCD5LJmd7A4ak1'
  AND NOT EXISTS (
    SELECT 1 FROM address a
    WHERE a.user_id = u.id AND a.street = 'ul. Marszałkowska 123/45'
  );

-- Address for Test Influencer 2  
INSERT INTO address (user_id, street, city, postal_code, country, state, additional_info, address_type, is_primary, source_type, is_shared, reference_count, is_copied)
SELECT 
    u.id,
    'ul. Floriańska 67',
    'Kraków',
    '31-019',
    'Polska',
    'Małopolskie',
    'Kamienica w centrum',
    'MAIN',
    true,
    'CUSTOM',
    false,
    1,
    false
FROM "user" u
WHERE u.firebase_user_id = '4tZkhv0j9KWKI2GquYs2at2aQvT2'
  AND NOT EXISTS (
    SELECT 1 FROM address a
    WHERE a.user_id = u.id AND a.street = 'ul. Floriańska 67'
  );

-- Address for Test Company 1
INSERT INTO address (user_id, street, city, postal_code, country, state, additional_info, address_type, is_primary, source_type, is_shared, reference_count, is_copied)
SELECT 
    u.id,
    'al. Jerozolimskie 200',
    'Warszawa',
    '02-486',
    'Polska',
    'Mazowieckie',
    'Biurowiec Test Tower, piętro 15',
    'MAIN',
    true,
    'CUSTOM',
    false,
    1,
    false
FROM "user" u
WHERE u.firebase_user_id = 'ZHXlEDREjKYHD0hOmi8ILm724su1'
  AND NOT EXISTS (
    SELECT 1 FROM address a
    WHERE a.user_id = u.id AND a.street = 'al. Jerozolimskie 200'
  );

-- Address for Test Company 2
INSERT INTO address (user_id, street, city, postal_code, country, state, additional_info, address_type, is_primary, source_type, is_shared, reference_count, is_copied)
SELECT 
    u.id,
    'ul. Grodzka 15',
    'Kraków',
    '31-006',
    'Polska',
    'Małopolskie',
    'Biuro w zabytkowej kamienicy',
    'MAIN',
    true,
    'CUSTOM',
    false,
    1,
    false
FROM "user" u
WHERE u.firebase_user_id = 'uVOlWYYmOvQsbq703Lo7BWeg3P23'
  AND NOT EXISTS (
    SELECT 1 FROM address a
    WHERE a.user_id = u.id AND a.street = 'ul. Grodzka 15'
  );

-- ============================================================================
-- SOCIAL CONNECTIONS - Add Instagram connections for both test influencers
-- ============================================================================

-- Social connection for Test Influencer 1 (Fashion/Beauty focused)
INSERT INTO user_social_connection (user_id, platform_id, social_user_id, profile_url, profile_picture_url, display_name, email, note, service_id, followers_count, is_primary, connection_status, last_sync_time)
SELECT 
    u.id,
    p.id,
    'test_influencer_1',
    'https://instagram.com/test_influencer_1',
    'https://example.com/profile1.jpg',
    'Test Influencer One | Fashion',
    'test.influencer@test.com',
    'TEST: Fashion & Beauty content creator | Primary Instagram connection for testing',
    s.id,
    15000,
    true,
    'CONNECTED',
    CURRENT_TIMESTAMP
FROM "user" u
CROSS JOIN platform p  
CROSS JOIN service_type s
WHERE u.firebase_user_id = 'cDKyirInmXNwVkBCD5LJmd7A4ak1'
  AND p.name = 'Instagram'
  AND s.name = 'Sklep odzieżowy'
  AND NOT EXISTS (
    SELECT 1 FROM user_social_connection usc
    WHERE usc.user_id = u.id AND usc.platform_id = p.id
  );

-- Social connection for Test Influencer 2 (Fitness focused)
INSERT INTO user_social_connection (user_id, platform_id, social_user_id, profile_url, profile_picture_url, display_name, email, note, service_id, followers_count, is_primary, connection_status, last_sync_time)
SELECT 
    u.id,
    p.id,
    'test_influencer_2', 
    'https://instagram.com/test_influencer_2',
    'https://example.com/profile2.jpg',
    'Test Influencer Two | Fitness',
    'test.influencer2@test.com',
    'TEST: Fitness & Health content creator | Primary Instagram connection for testing',
    s.id,
    25000,
    true,
    'CONNECTED',
    CURRENT_TIMESTAMP
FROM "user" u
CROSS JOIN platform p
CROSS JOIN service_type s
WHERE u.firebase_user_id = '4tZkhv0j9KWKI2GquYs2at2aQvT2'
  AND p.name = 'Instagram'
  AND s.name = 'Siłownia i fitness'
  AND NOT EXISTS (
    SELECT 1 FROM user_social_connection usc
    WHERE usc.user_id = u.id AND usc.platform_id = p.id
  );

-- rollback DELETE FROM user_social_connection WHERE note LIKE 'TEST%';
-- rollback DELETE FROM address WHERE additional_info LIKE '%TEST%' OR additional_info IN ('Apartament na 12 piętrze', 'Kamienica w centrum', 'Biurowiec Test Tower, piętro 15', 'Biuro w zabytkowej kamienicy');
-- rollback DELETE FROM applied_opportunity WHERE note LIKE 'TEST%';
-- rollback DELETE FROM partnership_opportunity WHERE name LIKE 'Test%';
-- rollback DELETE FROM "user" WHERE firebase_user_id LIKE '%TEST_%' OR firebase_user_id IN ('cDKyirInmXNwVkBCD5LJmd7A4ak1', '4tZkhv0j9KWKI2GquYs2at2aQvT2', 'ZHXlEDREjKYHD0hOmi8ILm724su1', 'uVOlWYYmOvQsbq703Lo7BWeg3P23');
