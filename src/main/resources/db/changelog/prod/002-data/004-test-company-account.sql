-- liquibase formatted sql
-- changeset system:004-test-company-account

-- ============================================================================
-- TEST COMPANY ACCOUNT - PRODUCTION ENVIRONMENT
-- ============================================================================

-- Test Company User Account
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
    'w12JjhF8ooMNKk0k4yUeeNu7lGk2',
    'COMPANY',
    'company@checkitout.app',
    'Test',
    'Company',
    'Test Company (Prod)',
    'https://picsum.photos/400/400?random=test-company-prod',
    '+48123456789',
    'Test company account for production testing purposes',
    'ACTIVE',
    NOW(),
    NOW()
)
ON CONFLICT (email) DO UPDATE SET
    firebase_user_id = EXCLUDED.firebase_user_id,
    account_status = EXCLUDED.account_status,
    note_from_admin = EXCLUDED.note_from_admin;

-- Insert company address
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
    (SELECT id FROM "user" WHERE email = 'company@checkitout.app'),
    'ul. Testowa 123',
    'Warszawa',
    '00-001',
    'Polska',
    'Mazowieckie',
    'Test Company Office - Production Environment',
    'MAIN',
    TRUE
);

-- rollback DELETE FROM address WHERE user_id = (SELECT id FROM "user" WHERE email = 'company@checkitout.app');
-- rollback DELETE FROM "user" WHERE email = 'company@checkitout.app';