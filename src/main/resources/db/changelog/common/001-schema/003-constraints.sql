-- ============================================================================
-- 003-CONSTRAINTS: CREATE ALL INDEXES AND CONSTRAINTS
-- ============================================================================
-- All indexes and unique constraints for optimal performance
-- FIELD SIZE CORRECTIONS: Fix mismatches between Java validation and DB constraints
-- ============================================================================

-- liquibase formatted sql
-- changeset system:003-create-all-constraints

-- ============================================================================
-- FIELD SIZE CORRECTIONS (Added to fix validation mismatches)
-- ============================================================================

-- Fix critical mismatch: PartnershipOpportunity.details
ALTER TABLE public.partnership_opportunity 
    ALTER COLUMN details TYPE VARCHAR(2000);

-- Fix missing VARCHAR lengths
ALTER TABLE public.partnership_opportunity
    ALTER COLUMN compensation_type TYPE VARCHAR(10),
    ALTER COLUMN compensation_description TYPE VARCHAR(500);

-- Fix missing VARCHAR lengths in user table
ALTER TABLE public."user" 
    ALTER COLUMN user_type TYPE VARCHAR(20),
    ALTER COLUMN account_status TYPE VARCHAR(20);

-- Fix missing VARCHAR lengths in other tables
ALTER TABLE user_social_connection
    ALTER COLUMN connection_status TYPE VARCHAR(20);

ALTER TABLE public.applied_opportunity
    ALTER COLUMN opportunity_status TYPE VARCHAR(30);

-- Optimize oversized fields in user_social_connection table
ALTER TABLE user_social_connection
    ALTER COLUMN email TYPE VARCHAR(255),           -- Reduce from 2048
    ALTER COLUMN display_name TYPE VARCHAR(100),    -- Reduce from 2048  
    ALTER COLUMN social_user_id TYPE VARCHAR(500);  -- Reduce from 2048

-- ============================================================================
-- UNIQUE INDEXES
-- ============================================================================

CREATE UNIQUE INDEX idx_user_preferences_user_id ON public.user_preferences(user_id);

-- ============================================================================
-- USER AND AUTHENTICATION INDEXES
-- ============================================================================

CREATE INDEX idx_user_email ON public."user"(email);
CREATE INDEX idx_user_user_type ON public."user"(user_type);
CREATE INDEX idx_user_account_status ON public."user"(account_status);

CREATE INDEX idx_user_social_user_id ON user_social_connection(social_user_id);
CREATE INDEX idx_user_social_user_id_only ON user_social_connection(user_id);
CREATE INDEX idx_user_social_platform_id ON user_social_connection(platform_id);

-- ============================================================================
-- ADDRESS INDEXES
-- ============================================================================

CREATE INDEX idx_address_user_id ON public.address(user_id);
CREATE INDEX idx_address_type ON public.address(address_type);
CREATE INDEX idx_address_is_primary ON public.address(is_primary);
CREATE INDEX idx_partnership_opportunity_address_id ON partnership_opportunity(address_id);
CREATE INDEX idx_address_source_copied ON address (source_address_id, is_copied) 
    WHERE is_copied = TRUE;

-- ============================================================================
-- CURRENCY INDEXES
-- ============================================================================

CREATE INDEX idx_currency_iso_code ON currency(iso_code);
CREATE INDEX idx_currency_country_code ON currency(country_code);
CREATE INDEX idx_currency_iso_country ON currency(iso_code, country_code);

-- ============================================================================
-- PARTNERSHIP OPPORTUNITY INDEXES
-- ============================================================================

CREATE INDEX idx_po_city_id ON public.partnership_opportunity(city_id);
CREATE INDEX idx_po_company_id ON public.partnership_opportunity(company_id);
CREATE INDEX idx_po_currency_id ON public.partnership_opportunity(currency_id);
CREATE INDEX idx_po_service_id ON public.partnership_opportunity(service_id);
CREATE INDEX idx_po_active ON public.partnership_opportunity(active);
CREATE INDEX idx_partnership_opportunity_applied_opportunities_index 
    ON applied_opportunity(partnership_opportunity_id);  -- Added 05-07-2025

CREATE INDEX idx_photo_opportunity_id ON public.partnership_opportunity_photo(partnership_opportunity_id);
CREATE INDEX idx_photo_is_cover ON public.partnership_opportunity_photo(is_cover);

-- ============================================================================
-- APPLIED OPPORTUNITY INDEXES
-- ============================================================================

CREATE INDEX idx_applied_opportunity_influencer_id ON public.applied_opportunity(influencer_id);
CREATE INDEX idx_applied_opportunity_status ON public.applied_opportunity(opportunity_status);

CREATE INDEX idx_applied_opportunity_status_history_applied_opportunity_id 
    ON applied_opportunity_status_history(applied_opportunity_id);
CREATE INDEX idx_applied_opportunity_status_history_changed_by_user_id 
    ON applied_opportunity_status_history(changed_by_user_id);
CREATE INDEX idx_applied_opportunity_status_history_changed_at 
    ON applied_opportunity_status_history(changed_at);
CREATE INDEX idx_applied_opportunity_status_history_new_status 
    ON applied_opportunity_status_history(new_status);

CREATE INDEX idx_applied_opportunity_content_applied_opportunity_id 
    ON applied_opportunity_content(applied_opportunity_id);
CREATE INDEX idx_applied_opportunity_content_content_type_id 
    ON applied_opportunity_content(content_type_id);
CREATE INDEX idx_applied_opportunity_content_approval_status 
    ON applied_opportunity_content(approval_status);
CREATE INDEX idx_applied_opportunity_content_submission_date 
    ON applied_opportunity_content(submission_date);
CREATE INDEX idx_applied_opportunity_content_created_time 
    ON applied_opportunity_content(created_time);

-- ============================================================================
-- PLATFORM AND CONTENT TYPE INDEXES
-- ============================================================================

CREATE INDEX idx_pct_platform_id ON platform_content_type(platform_id);
CREATE INDEX idx_pct_content_type_id ON platform_content_type(content_type_id);
CREATE INDEX idx_pop_platform_id ON partnership_opportunity_platform(platform_id);
CREATE INDEX idx_poct_content_type_id ON partnership_opportunity_content_type(content_type_id);

-- ============================================================================
-- SUPPORT SYSTEM INDEXES
-- ============================================================================

CREATE INDEX idx_support_ticket_status ON support_ticket(status);
CREATE INDEX idx_support_ticket_user_id ON support_ticket(user_id);
CREATE INDEX idx_support_ticket_reference ON support_ticket(ticket_reference);
CREATE INDEX idx_ticket_response_ticket_id ON ticket_response(ticket_id);

-- ============================================================================
-- HELP ARTICLE INDEXES
-- ============================================================================

CREATE INDEX idx_help_article_category_id ON help_article(category_id);
CREATE INDEX idx_help_article_slug ON help_article(slug);
CREATE INDEX idx_help_article_active ON help_article(active);

-- ============================================================================
-- FAQ INDEXES
-- ============================================================================

CREATE INDEX idx_faq_category_display_order ON faq_category(display_order);
CREATE INDEX idx_faq_display_order ON faq(display_order);
CREATE INDEX idx_faq_category_id ON faq(category_id);
CREATE INDEX idx_faq_active ON faq(active);
CREATE INDEX idx_faq_category_active ON faq_category(active);

-- ============================================================================
-- DICTIONARY INDEXES (Added 03-08-2025)
-- ============================================================================

CREATE UNIQUE INDEX idx_dictionary_unique_entry 
    ON dictionary_entries(entry_key, language_code);

-- rollback ALTER TABLE public.partnership_opportunity ALTER COLUMN details TYPE VARCHAR(255);
-- rollback ALTER TABLE public.partnership_opportunity ALTER COLUMN compensation_type TYPE VARCHAR;
-- rollback ALTER TABLE public.partnership_opportunity ALTER COLUMN compensation_description TYPE VARCHAR;
-- rollback ALTER TABLE public."user" ALTER COLUMN user_type TYPE VARCHAR;
-- rollback ALTER TABLE public."user" ALTER COLUMN account_status TYPE VARCHAR;
-- rollback ALTER TABLE user_social_connection ALTER COLUMN connection_status TYPE VARCHAR;
-- rollback ALTER TABLE public.applied_opportunity ALTER COLUMN opportunity_status TYPE VARCHAR;
-- rollback ALTER TABLE user_social_connection ALTER COLUMN email TYPE VARCHAR(2048);
-- rollback ALTER TABLE user_social_connection ALTER COLUMN display_name TYPE VARCHAR(2048);
-- rollback ALTER TABLE user_social_connection ALTER COLUMN social_user_id TYPE VARCHAR(2048);
-- rollback DROP INDEX IF EXISTS idx_user_preferences_user_id;
-- rollback DROP INDEX IF EXISTS idx_user_email;
-- rollback DROP INDEX IF EXISTS idx_user_user_type;
-- rollback DROP INDEX IF EXISTS idx_user_account_status;
-- rollback DROP INDEX IF EXISTS idx_user_social_user_id;
-- rollback DROP INDEX IF EXISTS idx_user_social_user_id_only;
-- rollback DROP INDEX IF EXISTS idx_user_social_platform_id;
-- rollback DROP INDEX IF EXISTS idx_address_user_id;
-- rollback DROP INDEX IF EXISTS idx_address_type;
-- rollback DROP INDEX IF EXISTS idx_address_is_primary;
-- rollback DROP INDEX IF EXISTS idx_partnership_opportunity_address_id;
-- rollback DROP INDEX IF EXISTS idx_address_source_copied;
-- rollback DROP INDEX IF EXISTS idx_currency_iso_code;
-- rollback DROP INDEX IF EXISTS idx_currency_country_code;
-- rollback DROP INDEX IF EXISTS idx_currency_iso_country;
-- rollback DROP INDEX IF EXISTS idx_po_city_id;
-- rollback DROP INDEX IF EXISTS idx_po_company_id;
-- rollback DROP INDEX IF EXISTS idx_po_currency_id;
-- rollback DROP INDEX IF EXISTS idx_po_service_id;
-- rollback DROP INDEX IF EXISTS idx_po_active;
-- rollback DROP INDEX IF EXISTS idx_partnership_opportunity_applied_opportunities_index;
-- rollback DROP INDEX IF EXISTS idx_photo_opportunity_id;
-- rollback DROP INDEX IF EXISTS idx_photo_is_cover;
-- rollback DROP INDEX IF EXISTS idx_applied_opportunity_influencer_id;
-- rollback DROP INDEX IF EXISTS idx_applied_opportunity_status;
-- rollback DROP INDEX IF EXISTS idx_applied_opportunity_status_history_applied_opportunity_id;
-- rollback DROP INDEX IF EXISTS idx_applied_opportunity_status_history_changed_by_user_id;
-- rollback DROP INDEX IF EXISTS idx_applied_opportunity_status_history_changed_at;
-- rollback DROP INDEX IF EXISTS idx_applied_opportunity_status_history_new_status;
-- rollback DROP INDEX IF EXISTS idx_applied_opportunity_content_applied_opportunity_id;
-- rollback DROP INDEX IF EXISTS idx_applied_opportunity_content_content_type_id;
-- rollback DROP INDEX IF EXISTS idx_applied_opportunity_content_approval_status;
-- rollback DROP INDEX IF EXISTS idx_applied_opportunity_content_submission_date;
-- rollback DROP INDEX IF EXISTS idx_applied_opportunity_content_created_time;
-- rollback DROP INDEX IF EXISTS idx_pct_platform_id;
-- rollback DROP INDEX IF EXISTS idx_pct_content_type_id;
-- rollback DROP INDEX IF EXISTS idx_pop_platform_id;
-- rollback DROP INDEX IF EXISTS idx_poct_content_type_id;
-- rollback DROP INDEX IF EXISTS idx_support_ticket_status;
-- rollback DROP INDEX IF EXISTS idx_support_ticket_user_id;
-- rollback DROP INDEX IF EXISTS idx_support_ticket_reference;
-- rollback DROP INDEX IF EXISTS idx_ticket_response_ticket_id;
-- rollback DROP INDEX IF EXISTS idx_help_article_category_id;
-- rollback DROP INDEX IF EXISTS idx_help_article_slug;
-- rollback DROP INDEX IF EXISTS idx_help_article_active;
-- rollback DROP INDEX IF EXISTS idx_faq_category_display_order;
-- rollback DROP INDEX IF EXISTS idx_faq_display_order;
-- rollback DROP INDEX IF EXISTS idx_faq_category_id;
-- rollback DROP INDEX IF EXISTS idx_faq_active;
-- rollback DROP INDEX IF EXISTS idx_faq_category_active;
-- rollback DROP INDEX IF EXISTS idx_dictionary_unique_entry;
