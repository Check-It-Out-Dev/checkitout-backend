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
    'ULQ46OrJeefXSTffmlEzDhVSfPv2',
    'ADMIN',
    'norbert.marchewka.prod@gmail.com',
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
    'q6filAoQeSPb5Yy0Omr1a19S2Dl1',
    'ADMIN',
    'piotr_zmudzki@checkitout.app',
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
    '04L9wTTXrYXOxQ13cEDP8iJRkxF2',
    'ADMIN',
    'kuba_sadowski@checkitout.app',
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

-- rollback DELETE FROM public."user" WHERE email IN ('norbert.marchewka.prod@gmail.com', 'piotr_zmudzki@checkitout.app', 'kuba_sadowski@checkitout.app');