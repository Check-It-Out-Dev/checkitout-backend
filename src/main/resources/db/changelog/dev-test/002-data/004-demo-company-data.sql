-- liquibase formatted sql

-- changeset liquibase:demo-company-data
-- Demo company account: Luxe Lifestyle Brands - Premium fashion & lifestyle retail company

-- Insert demo company user
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
    'QUJGQnlbN5UnnTyFWs9KycIPYnG3',
    'COMPANY',
    'company@checkitout.app',
    'Michael',
    'Thompson',
    'Luxe Lifestyle Brands',
    'https://picsum.photos/400/400?random=company-logo',
    '+48501234567',
    'Premium demo account - Multi-brand lifestyle company specializing in sustainable fashion and beauty. Full platform access.',
    'ACTIVE',
    NOW(),
    NOW()
);

-- Insert company address (flagship store location)
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
    'ul. Nowy Świat 21',
    'Warszawa',
    '00-029',
    'Polska',
    'Mazowieckie',
    'Luxe Lifestyle Flagship Store - Premium Business Center, 5th Floor',
    'MAIN',
    TRUE
);

-- Insert sample partnership opportunities for Luxe Lifestyle Brands
INSERT INTO partnership_opportunity (
    currency_id,
    name,
    city_id,
    company_id,
    title,
    details,
    requirements,
    compensation_type,
    compensation_amount_min,
    compensation_amount_max,
    followers_min,
    followers_max,
    compensation_description,
    start_date,
    end_date,
    service_id,
    active,
    updater_id
)
VALUES
(
    51, -- PLN
    'Sustainable Fashion Week 2025',
    1, -- Warszawa
    (SELECT id FROM "user" WHERE email = 'company@checkitout.app'),
    'Eco-Conscious Collection Launch',
    'Join us in launching our revolutionary sustainable fashion line made from recycled ocean plastics and organic materials. We need influencers who can authentically communicate our environmental mission while showcasing the style and quality of our pieces. The campaign includes exclusive preview access, professional photoshoots in our Warsaw showroom, and invitation to our Fashion Week launch event.',
    'Passion for sustainable fashion, high-quality content creation, minimum 5 feed posts + 10 stories over campaign period, authentic storytelling about sustainability, professional photography skills preferred.',
    'CASH',
    8000,
    25000,
    20000,
    150000,
    'Tiered compensation based on follower count and engagement rate. Additional bonuses for content performance exceeding KPIs.',
    '2025-09-15 10:00:00',
    '2025-11-30 22:00:00',
    351, -- Sklep odzieżowy (Fashion/Clothing store)
    TRUE,
    'demo_admin'
),
(
    51, -- PLN
    'Artisan Jewelry Showcase',
    51, -- Kraków
    (SELECT id FROM "user" WHERE email = 'company@checkitout.app'),
    'Handcrafted Luxury Jewelry Campaign',
    'Promote our exclusive collection of handcrafted jewelry pieces from local Polish artisans. Each piece tells a story of traditional craftsmanship meeting modern design. Selected influencers will receive a personalized jewelry set and create content showcasing the artistry and versatility of our pieces.',
    'Elegant aesthetic, attention to detail in content, audience interested in luxury lifestyle and artisan products, ability to create both photo and video content.',
    'BARTER',
    5000,
    15000,
    10000,
    100000,
    'Jewelry set worth 5000-8000 PLN plus monetary compensation. Long-term brand ambassador opportunity for top performers.',
    '2025-08-20 12:00:00',
    '2025-10-31 22:00:00',
    401, -- Sklep z biżuterią (Jewelry store)
    TRUE,
    'demo_admin'
),
(
    1, -- EUR
    'Summer Beauty Essentials',
    1, -- Warszawa
    (SELECT id FROM "user" WHERE email = 'company@checkitout.app'),
    'Clean Beauty Product Line Launch',
    'Introduce our new clean beauty line featuring organic, cruelty-free skincare and makeup products. We are looking for beauty enthusiasts who can create tutorials, reviews, and authentic testimonials about their experience with our products. Includes full product range and exclusive discount codes for followers.',
    'Beauty and skincare content focus, tutorial creation skills, genuine product reviews, diverse skin types welcome, minimum 3 tutorials + 5 product features.',
    'CASH',
    1500,
    8000,
    15000,
    200000,
    'Base fee plus commission on sales generated through personal discount codes. Free products for 6 months.',
    '2025-08-01 09:00:00',
    '2025-09-30 20:00:00',
    551, -- Studio urody (Beauty studio)
    TRUE,
    'demo_admin'
),
(
    51, -- PLN
    'Holiday Gift Guide Collaboration',
    101, -- Wrocław
    (SELECT id FROM "user" WHERE email = 'company@checkitout.app'),
    'Curated Holiday Shopping Experience',
    'Partner with us to create the ultimate holiday gift guide featuring our premium fashion, accessories, and beauty products. Create engaging content showing gift ideas for different personalities and budgets. Includes early access to holiday collections and special influencer-only bundles.',
    'Creative gift guide presentation, holiday content planning experience, family-friendly content, mix of photos, reels, and stories, shopping haul videos.',
    'CASH',
    6000,
    18000,
    25000,
    120000,
    'Competitive rates plus affiliate commission on holiday sales. Gift packages for personal use and giveaways.',
    '2025-11-01 10:00:00',
    '2025-12-31 22:00:00',
    351, -- Sklep odzieżowy (Fashion/Clothing store)
    TRUE,
    'demo_admin'
);

-- Add platforms for all partnership opportunities (Instagram, Facebook, YouTube)
INSERT INTO partnership_opportunity_platform (partnership_opportunity_id, platform_id)
SELECT po.id, p.id
FROM partnership_opportunity po
CROSS JOIN platform p
WHERE po.company_id = (SELECT id FROM "user" WHERE email = 'company@checkitout.app')
AND p.id IN (1, 51, 101); -- Instagram, Facebook, YouTube

-- Add content types for partnership opportunities
INSERT INTO partnership_opportunity_content_type (partnership_opportunity_id, content_type_id)
SELECT po.id, ct.id
FROM partnership_opportunity po
CROSS JOIN content_type ct
WHERE po.company_id = (SELECT id FROM "user" WHERE email = 'company@checkitout.app');

-- Add professional photos for each partnership opportunity
INSERT INTO partnership_opportunity_photo (partnership_opportunity_id, url, order_number, is_cover)
SELECT 
    po.id,
    CASE row_number() OVER (PARTITION BY po.id ORDER BY po.id)
        WHEN 1 THEN 'https://picsum.photos/800/600?random=' || po.id || '1'
        WHEN 2 THEN 'https://picsum.photos/800/600?random=' || po.id || '2'
        WHEN 3 THEN 'https://picsum.photos/800/600?random=' || po.id || '3'
        WHEN 4 THEN 'https://picsum.photos/800/600?random=' || po.id || '4'
    END,
    row_number() OVER (PARTITION BY po.id ORDER BY po.id),
    CASE WHEN row_number() OVER (PARTITION BY po.id ORDER BY po.id) = 1 THEN TRUE ELSE FALSE END
FROM partnership_opportunity po
CROSS JOIN generate_series(1, 4) AS gs
WHERE po.company_id = (SELECT id FROM "user" WHERE email = 'company@checkitout.app');