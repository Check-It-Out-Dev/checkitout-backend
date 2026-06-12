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
    'E2EADMINUID00000000000000001',
    'ADMIN',
    'e2e-admin@example.test',
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
    'SEEDADMINUID0000000000000003',
    'ADMIN',
    'admin2@example.com',
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
    'SEEDADMINUID0000000000000005',
    'ADMIN',
    'admin3@example.com',
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

-- rollback DELETE FROM public."user" WHERE email IN ('e2e-admin@example.test', 'admin2@example.com', 'admin3@example.com');