-- liquibase formatted sql
-- changeset system:003-admin-users-test

-- ============================================================================
-- ADMIN USERS - TEST ENVIRONMENT - Using ON CONFLICT for idempotency
-- ============================================================================

-- Test Admin (Norbert)
INSERT INTO public."user" (
    firebase_user_id,
    user_type,
    email,
    first_name,
    last_name,
    name,
    account_status,
    note_from_admin
) VALUES (
    'E2E_ADMIN_001',
    'ADMIN',
    'e2e.admin@test.com',
    'Norbert',
    'Marchewka',
    'Norbert Marchewka (Test)',
    'ACTIVE',
    'Test admin account - auto-created'
)
ON CONFLICT (email) DO UPDATE SET
    firebase_user_id = EXCLUDED.firebase_user_id,
    user_type = EXCLUDED.user_type,
    account_status = EXCLUDED.account_status,
    note_from_admin = EXCLUDED.note_from_admin;

-- Test Admin (Piotr) - Firebase ID to be filled
INSERT INTO public."user" (
    firebase_user_id,
    user_type,
    email,
    first_name,
    last_name,
    name,
    account_status,
    note_from_admin
) VALUES (
    'ifDF62o1FANsEs6milwNtwIcF8J2',
    'ADMIN',
    'piotr_zmudzki@checkitout.app',
    'Piotr',
    'Zmudzki',
    'Piotr Zmudzki (Test)',
    'ACTIVE',
    'Test admin account - auto-created'
)
ON CONFLICT (email) DO UPDATE SET
    firebase_user_id = EXCLUDED.firebase_user_id,
    user_type = EXCLUDED.user_type,
    account_status = EXCLUDED.account_status,
    note_from_admin = EXCLUDED.note_from_admin;

-- Test Admin (Kuba) - Firebase ID to be filled
INSERT INTO public."user" (
    firebase_user_id,
    user_type,
    email,
    first_name,
    last_name,
    name,
    account_status,
    note_from_admin
) VALUES (
    'v3xNWljzWHVO8P93BbWR3DhdUDm1',
    'ADMIN',
    'kuba_sadowski@checkitout.app',
    'Kuba',
    'Sadowski',
    'Kuba Sadowski (Test)',
    'ACTIVE',
    'Test admin account - auto-created'
)
ON CONFLICT (email) DO UPDATE SET
    firebase_user_id = EXCLUDED.firebase_user_id,
    user_type = EXCLUDED.user_type,
    account_status = EXCLUDED.account_status,
    note_from_admin = EXCLUDED.note_from_admin;

-- rollback DELETE FROM public."user" WHERE email IN ('e2e.admin@test.com', 'piotr_zmudzki@checkitout.app', 'kuba_sadowski@checkitout.app');