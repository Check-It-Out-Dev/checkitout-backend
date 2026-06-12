-- liquibase formatted sql

-- changeset liquibase:demo-influencer-data
-- Demo influencer account: Sofia Kowalska - Fashion, Beauty & Lifestyle content creator

-- Insert demo influencer user
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
    'tKJDgYP5B7aP0mMg5oMgiAOIuCI2',
    'INFLUENCER',
    'influencer@checkitout.app',
    'Sofia',
    'Kowalska',
    'Sofia Kowalska',
    'https://picsum.photos/400/400?random=influencer-profile',
    '+48509876543',
    'Premium demo influencer account - Fashion & lifestyle content creator with authentic engagement. Full platform access.',
    'ACTIVE',
    NOW(),
    NOW()
);

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
    'ul. Mokotowska 45/12',
    'Warszawa',
    '00-551',
    'Polska',
    'Mazowieckie',
    'Apartment 12, 3rd Floor',
    'MAIN',
    TRUE
);

-- Insert Instagram social connection for Sofia
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
    'sofia.lifestyle',
    'https://instagram.com/sofia.lifestyle',
    'https://picsum.photos/400/400?random=influencer-instagram',
    'Sofia | Fashion & Lifestyle',
    'influencer@checkitout.app',
    'Fashion blogger | Beauty enthusiast | Sustainable lifestyle advocate | Warsaw 📍 | Collaborations: influencer@checkitout.app',
    351, -- Fashion service (Sklep odzieżowy)
    45000,
    TRUE,
    'CONNECTED',
    NOW()
);