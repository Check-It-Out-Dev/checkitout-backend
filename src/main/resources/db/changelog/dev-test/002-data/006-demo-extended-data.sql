-- liquibase formatted sql

-- changeset Jakub Sadowski:demo-extended-data
-- Extended demo data: Comprehensive Partnership/Applied Opportunities covering all statuses
-- Uses existing demo users: Luxe Lifestyle Brands (company) + Sofia Kowalska (influencer)
-- Each Applied Opportunity uses a unique Partnership Opportunity to respect constraints

-- ============================================================================
-- PARTNERSHIP OPPORTUNITIES - Create 12 unique POs for all 12 Applied Opportunity statuses
-- ============================================================================

-- 1. PO for APPLIED Status
INSERT INTO partnership_opportunity (
    currency_id, name, city_id, company_id, title, details, requirements,
    compensation_type, compensation_amount_min, compensation_amount_max,
    followers_min, followers_max, compensation_description,
    start_date, end_date, service_id, active, updater_id
)
SELECT cur.id, 'Demo Applied Status PO', c.id, u.id, 'Fashion Content - Applied Stage',
       'Fashion content creation for new collection showcase', 'Fashion expertise required',
       'CASH', 3000, 8000, 10000, 80000, 'Applied stage partnership',
       '2025-09-01 09:00:00', '2025-09-30 22:00:00', s.id, TRUE, 'Jakub Sadowski'
FROM "user" u, city c, service_type s, currency cur
WHERE u.email = 'company@checkitout.app' AND c.name = 'Warszawa' 
  AND s.name = 'Sklep odzieżowy' AND cur.iso_code = 'PLN'
  AND NOT EXISTS (SELECT 1 FROM partnership_opportunity WHERE name = 'Demo Applied Status PO');

-- 2. PO for ACCEPTED_BY_COMPANY Status
INSERT INTO partnership_opportunity (
    currency_id, name, city_id, company_id, title, details, requirements,
    compensation_type, compensation_amount_min, compensation_amount_max,
    followers_min, followers_max, compensation_description,
    start_date, end_date, service_id, active, updater_id
)
SELECT cur.id, 'Demo Accepted By Company PO', c.id, u.id, 'Beauty Content - Company Accepted',
       'Beauty routine content creation with clean products', 'Beauty content specialization',
       'BARTER', 2000, 6000, 15000, 100000, 'Company accepted partnership',
       '2025-09-15 08:00:00', '2025-10-15 20:00:00', s.id, TRUE, 'Jakub Sadowski'
FROM "user" u, city c, service_type s, currency cur
WHERE u.email = 'company@checkitout.app' AND c.name = 'Kraków' 
  AND s.name = 'Sklep z kosmetykami' AND cur.iso_code = 'EUR'
  AND NOT EXISTS (SELECT 1 FROM partnership_opportunity WHERE name = 'Demo Accepted By Company PO');

-- 3. PO for REJECTED_BY_COMPANY Status
INSERT INTO partnership_opportunity (
    currency_id, name, city_id, company_id, title, details, requirements,
    compensation_type, compensation_amount_min, compensation_amount_max,
    followers_min, followers_max, compensation_description,
    start_date, end_date, service_id, active, updater_id
)
SELECT cur.id, 'Demo Rejected By Company PO', c.id, u.id, 'Jewelry Content - Company Rejected',
       'Luxury jewelry storytelling content creation', 'High-quality photography skills',
       'CASH', 4000, 12000, 20000, 150000, 'Company rejected partnership',
       '2025-08-20 10:00:00', '2025-11-20 22:00:00', s.id, TRUE, 'Jakub Sadowski'
FROM "user" u, city c, service_type s, currency cur
WHERE u.email = 'company@checkitout.app' AND c.name = 'Wrocław' 
  AND s.name = 'Sklep z biżuterią' AND cur.iso_code = 'USD'
  AND NOT EXISTS (SELECT 1 FROM partnership_opportunity WHERE name = 'Demo Rejected By Company PO');

-- 4. PO for ACCEPTED_BY_INFLUENCER Status
INSERT INTO partnership_opportunity (
    currency_id, name, city_id, company_id, title, details, requirements,
    compensation_type, compensation_amount_min, compensation_amount_max,
    followers_min, followers_max, compensation_description,
    start_date, end_date, service_id, active, updater_id
)
SELECT cur.id, 'Demo Accepted By Influencer PO', c.id, u.id, 'Wellness Content - Influencer Accepted',
       'Wellness lifestyle integration with premium products', 'Wellness content expertise',
       'CASH', 5000, 15000, 30000, 200000, 'Influencer accepted partnership',
       '2025-10-01 09:00:00', '2025-12-01 21:00:00', s.id, TRUE, 'Jakub Sadowski'
FROM "user" u, city c, service_type s, currency cur
WHERE u.email = 'company@checkitout.app' AND c.name = 'Poznań' 
  AND s.name = 'Studio urody' AND cur.iso_code = 'PLN'
  AND NOT EXISTS (SELECT 1 FROM partnership_opportunity WHERE name = 'Demo Accepted By Influencer PO');

-- 5. PO for REJECTED_BY_INFLUENCER Status
INSERT INTO partnership_opportunity (
    currency_id, name, city_id, company_id, title, details, requirements,
    compensation_type, compensation_amount_min, compensation_amount_max,
    followers_min, followers_max, compensation_description,
    start_date, end_date, service_id, active, updater_id
)
SELECT cur.id, 'Demo Rejected By Influencer PO', c.id, u.id, 'Gift Guide - Influencer Rejected',
       'Holiday gift guide creation with premium products', 'Holiday content expertise',
       'BARTER', 3000, 10000, 15000, 120000, 'Influencer rejected partnership',
       '2025-11-15 10:00:00', '2025-12-31 23:00:00', s.id, TRUE, 'Jakub Sadowski'
FROM "user" u, city c, service_type s, currency cur
WHERE u.email = 'company@checkitout.app' AND c.name = 'Łódź' 
  AND s.name = 'Sklep odzieżowy' AND cur.iso_code = 'EUR'
  AND NOT EXISTS (SELECT 1 FROM partnership_opportunity WHERE name = 'Demo Rejected By Influencer PO');

-- 6. PO for CONTENT_SEND_TO_ACCEPT Status
INSERT INTO partnership_opportunity (
    currency_id, name, city_id, company_id, title, details, requirements,
    compensation_type, compensation_amount_min, compensation_amount_max,
    followers_min, followers_max, compensation_description,
    start_date, end_date, service_id, active, updater_id
)
SELECT cur.id, 'Demo Content Send To Accept PO', c.id, u.id, 'Lifestyle Content - Submitted',
       'Authentic lifestyle content with product integration', 'Lifestyle content creation',
       'CASH', 2500, 7500, 12000, 90000, 'Content submitted for approval',
       '2025-08-10 09:00:00', '2025-09-10 21:00:00', s.id, TRUE, 'Jakub Sadowski'
FROM "user" u, city c, service_type s, currency cur
WHERE u.email = 'company@checkitout.app' AND c.name = 'Katowice' 
  AND s.name = 'Sklep odzieżowy' AND cur.iso_code = 'PLN'
  AND NOT EXISTS (SELECT 1 FROM partnership_opportunity WHERE name = 'Demo Content Send To Accept PO');

-- 7. PO for CONTENT_APPROVED Status
INSERT INTO partnership_opportunity (
    currency_id, name, city_id, company_id, title, details, requirements,
    compensation_type, compensation_amount_min, compensation_amount_max,
    followers_min, followers_max, compensation_description,
    start_date, end_date, service_id, active, updater_id
)
SELECT cur.id, 'Demo Content Approved PO', c.id, u.id, 'Accessory Content - Approved',
       'Premium accessory showcase with elegant presentation', 'Luxury content expertise',
       'CASH', 6000, 18000, 25000, 180000, 'Content approved for posting',
       '2025-08-01 10:00:00', '2025-10-01 22:00:00', s.id, TRUE, 'Jakub Sadowski'
FROM "user" u, city c, service_type s, currency cur
WHERE u.email = 'company@checkitout.app' AND c.name = 'Szczecin' 
  AND s.name = 'Sklep z biżuterią' AND cur.iso_code = 'EUR'
  AND NOT EXISTS (SELECT 1 FROM partnership_opportunity WHERE name = 'Demo Content Approved PO');

-- 8. PO for CONTENT_REJECTED Status
INSERT INTO partnership_opportunity (
    currency_id, name, city_id, company_id, title, details, requirements,
    compensation_type, compensation_amount_min, compensation_amount_max,
    followers_min, followers_max, compensation_description,
    start_date, end_date, service_id, active, updater_id
)
SELECT cur.id, 'Demo Content Rejected PO', c.id, u.id, 'Fashion Content - Needs Revision',
       'Fashion content requiring specific styling approach', 'Specific aesthetic requirements',
       'BARTER', 2500, 8000, 10000, 70000, 'Content needs revision',
       '2025-09-05 10:00:00', '2025-10-05 20:00:00', s.id, TRUE, 'Jakub Sadowski'
FROM "user" u, city c, service_type s, currency cur
WHERE u.email = 'company@checkitout.app' AND c.name = 'Lublin' 
  AND s.name = 'Sklep odzieżowy' AND cur.iso_code = 'USD'
  AND NOT EXISTS (SELECT 1 FROM partnership_opportunity WHERE name = 'Demo Content Rejected PO');

-- 9. PO for CONTENT_POSTED Status
INSERT INTO partnership_opportunity (
    currency_id, name, city_id, company_id, title, details, requirements,
    compensation_type, compensation_amount_min, compensation_amount_max,
    followers_min, followers_max, compensation_description,
    start_date, end_date, service_id, active, updater_id
)
SELECT cur.id, 'Demo Content Posted PO', c.id, u.id, 'Beauty Content - Successfully Posted',
       'Beauty content with high engagement potential', 'Proven engagement track record',
       'CASH', 4500, 13500, 20000, 150000, 'Content successfully posted',
       '2025-08-15 09:00:00', '2025-09-15 21:00:00', s.id, TRUE, 'Jakub Sadowski'
FROM "user" u, city c, service_type s, currency cur
WHERE u.email = 'company@checkitout.app' AND c.name = 'Białystok' 
  AND s.name = 'Studio urody' AND cur.iso_code = 'PLN'
  AND NOT EXISTS (SELECT 1 FROM partnership_opportunity WHERE name = 'Demo Content Posted PO');

-- 10. PO for CONTENT_POSTED_REJECTED Status
INSERT INTO partnership_opportunity (
    currency_id, name, city_id, company_id, title, details, requirements,
    compensation_type, compensation_amount_min, compensation_amount_max,
    followers_min, followers_max, compensation_description,
    start_date, end_date, service_id, active, updater_id
)
SELECT cur.id, 'Demo Content Posted Rejected PO', c.id, u.id, 'Lifestyle Content - Posted Rejected',
       'Lifestyle content with specific brand guidelines', 'Strict brand guideline adherence',
       'CASH', 3500, 9500, 15000, 100000, 'Posted content needs correction',
       '2025-08-25 10:00:00', '2025-09-25 20:00:00', s.id, TRUE, 'Jakub Sadowski'
FROM "user" u, city c, service_type s, currency cur
WHERE u.email = 'company@checkitout.app' AND c.name = 'Rzeszów' 
  AND s.name = 'Sklep odzieżowy' AND cur.iso_code = 'EUR'
  AND NOT EXISTS (SELECT 1 FROM partnership_opportunity WHERE name = 'Demo Content Posted Rejected PO');

-- 11. PO for TO_BE_PAID Status
INSERT INTO partnership_opportunity (
    currency_id, name, city_id, company_id, title, details, requirements,
    compensation_type, compensation_amount_min, compensation_amount_max,
    followers_min, followers_max, compensation_description,
    start_date, end_date, service_id, active, updater_id
)
SELECT cur.id, 'Demo To Be Paid PO', c.id, u.id, 'Premium Content - Awaiting Payment',
       'Premium content creation with exceptional performance', 'Premium quality standards',
       'CASH', 7000, 20000, 35000, 250000, 'Awaiting payment processing',
       '2025-07-15 10:00:00', '2025-08-30 22:00:00', s.id, TRUE, 'Jakub Sadowski'
FROM "user" u, city c, service_type s, currency cur
WHERE u.email = 'company@checkitout.app' AND c.name = 'Częstochowa' 
  AND s.name = 'Sklep z biżuterią' AND cur.iso_code = 'USD'
  AND NOT EXISTS (SELECT 1 FROM partnership_opportunity WHERE name = 'Demo To Be Paid PO');

-- 12. PO for DONE Status (Completed)
INSERT INTO partnership_opportunity (
    currency_id, name, city_id, company_id, title, details, requirements,
    compensation_type, compensation_amount_min, compensation_amount_max,
    followers_min, followers_max, compensation_description,
    start_date, end_date, service_id, active, updater_id
)
SELECT cur.id, 'Demo Done Status PO', c.id, u.id, 'Completed Campaign - Success',
       'Successfully completed fashion campaign with excellent results', 'Proven success record',
       'CASH', 8000, 25000, 40000, 300000, 'Successfully completed campaign',
       '2025-06-01 10:00:00', '2025-07-31 22:00:00', s.id, FALSE, 'Jakub Sadowski'
FROM "user" u, city c, service_type s, currency cur
WHERE u.email = 'company@checkitout.app' AND c.name = 'Toruń' 
  AND s.name = 'Studio urody' AND cur.iso_code = 'PLN'
  AND NOT EXISTS (SELECT 1 FROM partnership_opportunity WHERE name = 'Demo Done Status PO');

-- ============================================================================
-- APPLIED OPPORTUNITIES - All 12 OpportunityStatus values (1 per unique PO)
-- ============================================================================

-- 1. APPLIED Status
INSERT INTO applied_opportunity (partnership_opportunity_id, influencer_id, note, opportunity_status, rate_status, company_rate_status, updater_id)
SELECT po.id, u.id, 'DEMO: Excited about this fashion opportunity! My style aligns perfectly with your brand aesthetic.', 
       'APPLIED', 'DEFAULT', 'DEFAULT', u.firebase_user_id
FROM partnership_opportunity po, "user" u
WHERE po.name = 'Demo Applied Status PO' AND u.email = 'influencer@checkitout.app'
  AND NOT EXISTS (SELECT 1 FROM applied_opportunity WHERE partnership_opportunity_id = po.id AND influencer_id = u.id);

-- 2. ACCEPTED_BY_COMPANY Status  
INSERT INTO applied_opportunity (partnership_opportunity_id, influencer_id, note, opportunity_status, rate_status, company_rate_status, updater_id)
SELECT po.id, u.id, 'DEMO: Thank you for accepting my application! Ready to start creating amazing beauty content.', 
       'ACCEPTED_BY_COMPANY', 'DEFAULT', 'DEFAULT', u.firebase_user_id
FROM partnership_opportunity po, "user" u
WHERE po.name = 'Demo Accepted By Company PO' AND u.email = 'influencer@checkitout.app'
  AND NOT EXISTS (SELECT 1 FROM applied_opportunity WHERE partnership_opportunity_id = po.id AND influencer_id = u.id);

-- 3. REJECTED_BY_COMPANY Status
INSERT INTO applied_opportunity (partnership_opportunity_id, influencer_id, note, opportunity_status, rate_status, company_rate_status, updater_id)
SELECT po.id, u.id, 'DEMO: Disappointed about the rejection, but I understand your selection process.', 
       'REJECTED_BY_COMPANY', 'NEGATIVE', 'DEFAULT', u.firebase_user_id
FROM partnership_opportunity po, "user" u
WHERE po.name = 'Demo Rejected By Company PO' AND u.email = 'influencer@checkitout.app'
  AND NOT EXISTS (SELECT 1 FROM applied_opportunity WHERE partnership_opportunity_id = po.id AND influencer_id = u.id);

-- 4. ACCEPTED_BY_INFLUENCER Status
INSERT INTO applied_opportunity (partnership_opportunity_id, influencer_id, note, opportunity_status, rate_status, company_rate_status, updater_id)
SELECT po.id, u.id, 'DEMO: Accepting this collaboration! Looking forward to creating wellness content together.', 
       'ACCEPTED_BY_INFLUENCER', 'DEFAULT', 'POSITIVE', u.firebase_user_id
FROM partnership_opportunity po, "user" u
WHERE po.name = 'Demo Accepted By Influencer PO' AND u.email = 'influencer@checkitout.app'
  AND NOT EXISTS (SELECT 1 FROM applied_opportunity WHERE partnership_opportunity_id = po.id AND influencer_id = u.id);

-- 5. REJECTED_BY_INFLUENCER Status
INSERT INTO applied_opportunity (partnership_opportunity_id, influencer_id, note, opportunity_status, rate_status, company_rate_status, updater_id)
SELECT po.id, u.id, 'DEMO: After consideration, this collaboration doesn''t align with my current content strategy.', 
       'REJECTED_BY_INFLUENCER', 'DEFAULT', 'NEGATIVE', u.firebase_user_id
FROM partnership_opportunity po, "user" u
WHERE po.name = 'Demo Rejected By Influencer PO' AND u.email = 'influencer@checkitout.app'
  AND NOT EXISTS (SELECT 1 FROM applied_opportunity WHERE partnership_opportunity_id = po.id AND influencer_id = u.id);

-- 6. CONTENT_SEND_TO_ACCEPT Status
INSERT INTO applied_opportunity (partnership_opportunity_id, influencer_id, note, opportunity_status, rate_status, company_rate_status, updater_id)
SELECT po.id, u.id, 'DEMO: Content submitted for review! Created lifestyle integration posts with authentic product placement.', 
       'CONTENT_SEND_TO_ACCEPT', 'DEFAULT', 'DEFAULT', u.firebase_user_id
FROM partnership_opportunity po, "user" u
WHERE po.name = 'Demo Content Send To Accept PO' AND u.email = 'influencer@checkitout.app'
  AND NOT EXISTS (SELECT 1 FROM applied_opportunity WHERE partnership_opportunity_id = po.id AND influencer_id = u.id);

-- 7. CONTENT_APPROVED Status
INSERT INTO applied_opportunity (partnership_opportunity_id, influencer_id, note, opportunity_status, rate_status, company_rate_status, updater_id)
SELECT po.id, u.id, 'DEMO: Great news! Content approved. Ready to post premium accessory showcase.', 
       'CONTENT_APPROVED', 'DEFAULT', 'POSITIVE', u.firebase_user_id
FROM partnership_opportunity po, "user" u
WHERE po.name = 'Demo Content Approved PO' AND u.email = 'influencer@checkitout.app'
  AND NOT EXISTS (SELECT 1 FROM applied_opportunity WHERE partnership_opportunity_id = po.id AND influencer_id = u.id);

-- 8. CONTENT_REJECTED Status
INSERT INTO applied_opportunity (partnership_opportunity_id, influencer_id, note, opportunity_status, rate_status, company_rate_status, updater_id)
SELECT po.id, u.id, 'DEMO: Content feedback received. Will revise fashion content with better brand alignment.', 
       'CONTENT_REJECTED', 'DEFAULT', 'DEFAULT', u.firebase_user_id
FROM partnership_opportunity po, "user" u
WHERE po.name = 'Demo Content Rejected PO' AND u.email = 'influencer@checkitout.app'
  AND NOT EXISTS (SELECT 1 FROM applied_opportunity WHERE partnership_opportunity_id = po.id AND influencer_id = u.id);

-- 9. CONTENT_POSTED Status
INSERT INTO applied_opportunity (partnership_opportunity_id, influencer_id, note, opportunity_status, rate_status, company_rate_status, updater_id)
SELECT po.id, u.id, 'DEMO: Content posted successfully! Receiving excellent engagement and positive feedback from audience.', 
       'CONTENT_POSTED', 'POSITIVE', 'POSITIVE', u.firebase_user_id
FROM partnership_opportunity po, "user" u
WHERE po.name = 'Demo Content Posted PO' AND u.email = 'influencer@checkitout.app'
  AND NOT EXISTS (SELECT 1 FROM applied_opportunity WHERE partnership_opportunity_id = po.id AND influencer_id = u.id);

-- 10. CONTENT_POSTED_REJECTED Status
INSERT INTO applied_opportunity (partnership_opportunity_id, influencer_id, note, opportunity_status, rate_status, company_rate_status, updater_id)
SELECT po.id, u.id, 'DEMO: Posted content but needs correction - missing required hashtags. Fixing now!', 
       'CONTENT_POSTED_REJECTED', 'DEFAULT', 'DEFAULT', u.firebase_user_id
FROM partnership_opportunity po, "user" u
WHERE po.name = 'Demo Content Posted Rejected PO' AND u.email = 'influencer@checkitout.app'
  AND NOT EXISTS (SELECT 1 FROM applied_opportunity WHERE partnership_opportunity_id = po.id AND influencer_id = u.id);

-- 11. TO_BE_PAID Status
INSERT INTO applied_opportunity (partnership_opportunity_id, influencer_id, note, opportunity_status, rate_status, company_rate_status, updater_id)
SELECT po.id, u.id, 'DEMO: Campaign completed with outstanding results! Awaiting final payment processing.', 
       'TO_BE_PAID', 'POSITIVE', 'POSITIVE', u.firebase_user_id
FROM partnership_opportunity po, "user" u
WHERE po.name = 'Demo To Be Paid PO' AND u.email = 'influencer@checkitout.app'
  AND NOT EXISTS (SELECT 1 FROM applied_opportunity WHERE partnership_opportunity_id = po.id AND influencer_id = u.id);

-- 12. DONE Status
INSERT INTO applied_opportunity (partnership_opportunity_id, influencer_id, note, opportunity_status, rate_status, company_rate_status, updater_id)
SELECT po.id, u.id, 'DEMO: Fantastic collaboration completed! Exceeded expectations and built great brand partnership.', 
       'DONE', 'POSITIVE', 'POSITIVE', u.firebase_user_id
FROM partnership_opportunity po, "user" u
WHERE po.name = 'Demo Done Status PO' AND u.email = 'influencer@checkitout.app'
  AND NOT EXISTS (SELECT 1 FROM applied_opportunity WHERE partnership_opportunity_id = po.id AND influencer_id = u.id);

-- ============================================================================
-- ADD PLATFORMS AND CONTENT TYPES FOR ALL NEW PARTNERSHIP OPPORTUNITIES
-- ============================================================================

-- Add Instagram platform for all new partnership opportunities
INSERT INTO partnership_opportunity_platform (partnership_opportunity_id, platform_id)
SELECT po.id, 1 -- Instagram
FROM partnership_opportunity po
WHERE po.company_id = (SELECT id FROM "user" WHERE email = 'company@checkitout.app')
AND po.name LIKE 'Demo %'
AND NOT EXISTS (
    SELECT 1 FROM partnership_opportunity_platform pop 
    WHERE pop.partnership_opportunity_id = po.id AND pop.platform_id = 1
);

-- Add content types (photo, reel, story) for all new partnership opportunities
INSERT INTO partnership_opportunity_content_type (partnership_opportunity_id, content_type_id)
SELECT po.id, ct.id
FROM partnership_opportunity po
CROSS JOIN content_type ct
WHERE po.company_id = (SELECT id FROM "user" WHERE email = 'company@checkitout.app')
AND po.name LIKE 'Demo %'
AND ct.name IN ('photo', 'reel', 'story')
AND NOT EXISTS (
    SELECT 1 FROM partnership_opportunity_content_type poct 
    WHERE poct.partnership_opportunity_id = po.id AND poct.content_type_id = ct.id
);

-- Add sample photos for all new partnership opportunities
INSERT INTO partnership_opportunity_photo (partnership_opportunity_id, url, order_number, is_cover)
SELECT 
    po.id,
    'https://picsum.photos/800/600?random=demo' || po.id || gs.num,
    gs.num,
    CASE WHEN gs.num = 1 THEN TRUE ELSE FALSE END
FROM partnership_opportunity po
CROSS JOIN generate_series(1, 3) AS gs(num)
WHERE po.company_id = (SELECT id FROM "user" WHERE email = 'company@checkitout.app')
AND po.name LIKE 'Demo %'
AND NOT EXISTS (
    SELECT 1 FROM partnership_opportunity_photo pop 
    WHERE pop.partnership_opportunity_id = po.id AND pop.order_number = gs.num
);

-- rollback DELETE FROM partnership_opportunity_photo WHERE url LIKE '%demo%' AND partnership_opportunity_id IN (SELECT id FROM partnership_opportunity WHERE name LIKE 'Demo %');
-- rollback DELETE FROM partnership_opportunity_content_type WHERE partnership_opportunity_id IN (SELECT id FROM partnership_opportunity WHERE name LIKE 'Demo %');
-- rollback DELETE FROM partnership_opportunity_platform WHERE partnership_opportunity_id IN (SELECT id FROM partnership_opportunity WHERE name LIKE 'Demo %');
-- rollback DELETE FROM applied_opportunity WHERE partnership_opportunity_id IN (SELECT id FROM partnership_opportunity WHERE name LIKE 'Demo %');
-- rollback DELETE FROM partnership_opportunity WHERE name LIKE 'Demo %';
