-- liquibase formatted sql
-- changeset system:003-admin-users-prod

-- ============================================================================
-- ADMIN USERS - PRODUCTION ENVIRONMENT - Using ON CONFLICT for idempotency
-- ============================================================================

-- Production Admin (Norbert)
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
    'SEEDADMINUID0000000000000002',
    'ADMIN',
    'admin@example.com',
    'Norbert',
    'Marchewka',
    'Norbert Marchewka (Prod)',
    'ACTIVE',
    'Production admin account - auto-created'
)
ON CONFLICT (email) DO UPDATE SET
    firebase_user_id = EXCLUDED.firebase_user_id,
    user_type = EXCLUDED.user_type,
    account_status = EXCLUDED.account_status,
    note_from_admin = EXCLUDED.note_from_admin;

-- Production Admin (Piotr) - Firebase ID to be filled
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
    'SEEDADMINUID0000000000000004',
    'ADMIN',
    'admin2@example.com',
    'Piotr',
    'Zmudzki',
    'Piotr Zmudzki (Prod)',
    'ACTIVE',
    'Production admin account - auto-created'
)
ON CONFLICT (email) DO UPDATE SET
    firebase_user_id = EXCLUDED.firebase_user_id,
    user_type = EXCLUDED.user_type,
    account_status = EXCLUDED.account_status,
    note_from_admin = EXCLUDED.note_from_admin;

-- Production Admin (Kuba) - Firebase ID to be filled
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
    'SEEDADMINUID0000000000000001',
    'ADMIN',
    'admin3@example.com',
    'Kuba',
    'Sadowski',
    'Kuba Sadowski (Prod)',
    'ACTIVE',
    'Production admin account - auto-created'
)
ON CONFLICT (email) DO UPDATE SET
    firebase_user_id = EXCLUDED.firebase_user_id,
    user_type = EXCLUDED.user_type,
    account_status = EXCLUDED.account_status,
    note_from_admin = EXCLUDED.note_from_admin;

-- rollback DELETE FROM public."user" WHERE email IN ('admin@example.com', 'admin2@example.com', 'admin3@example.com');