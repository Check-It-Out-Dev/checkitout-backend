-- liquibase formatted sql
-- changeset system:005-test-influencer-account

-- ============================================================================
-- TEST INFLUENCER ACCOUNT - PRODUCTION ENVIRONMENT
-- ============================================================================

-- Test Influencer User Account
INSERT INTO "user" (
    firebase_user_id, 
    user_type, 
    email, 
    first_name, 
    last_name, 
    name,
    profile_picture,
    phone_number,
    note_from_admin,
    account_status, 
    created_time,
    last_update_time
)
VALUES (
    'nzbUHLrZtaRMTvHqu8BRjHDdlx62',
    'INFLUENCER',
    'influencer@checkitout.app',
    'Test',
    'Influencer',
    'Test Influencer (Prod)',
    'https://picsum.photos/400/400?random=test-influencer-prod',
    '+48987654321',
    'Test influencer account for production testing purposes',
    'ACTIVE',
    NOW(),
    NOW()
)
ON CONFLICT (email) DO UPDATE SET
    firebase_user_id = EXCLUDED.firebase_user_id,
    account_status = EXCLUDED.account_status,
    note_from_admin = EXCLUDED.note_from_admin;

-- Insert influencer address
INSERT INTO address (
    user_id,
    street,
    city,
    postal_code,
    country,
    state,
    additional_info,
    address_type,
    is_primary
)
VALUES (
    (SELECT id FROM "user" WHERE email = 'influencer@checkitout.app'),
    'ul. Mokotowska 67/15',
    'Warszawa',
    '00-551',
    'Polska',
    'Mazowieckie',
    'Test Influencer Apartment - Production Environment',
    'MAIN',
    TRUE
);

-- Insert Instagram social connection
INSERT INTO user_social_connection (
    user_id,
    platform_id,
    social_user_id,
    profile_url,
    profile_picture_url,
    display_name,
    email,
    note,
    service_id,
    followers_count,
    is_primary,
    connection_status,
    last_sync_time
)
VALUES (
    (SELECT id FROM "user" WHERE email = 'influencer@checkitout.app'),
    1, -- Instagram
    'test.influencer.prod',
    'https://instagram.com/test.influencer.prod',
    'https://picsum.photos/400/400?random=test-influencer-instagram-prod',
    'Test Influencer | Prod Testing',
    'influencer@checkitout.app',
    'Test influencer account for production testing | Warsaw 📍 | Testing: influencer@checkitout.app',
    351, -- Fashion service (Sklep odzieżowy)
    25000,
    TRUE,
    'CONNECTED',
    NOW()
);

-- rollback DELETE FROM user_social_connection WHERE user_id = (SELECT id FROM "user" WHERE email = 'influencer@checkitout.app');
-- rollback DELETE FROM address WHERE user_id = (SELECT id FROM "user" WHERE email = 'influencer@checkitout.app');
-- rollback DELETE FROM "user" WHERE email = 'influencer@checkitout.app';