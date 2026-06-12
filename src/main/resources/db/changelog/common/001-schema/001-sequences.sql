-- ============================================================================
-- 001-SEQUENCES: CREATE ALL APPLICATION SEQUENCES
-- ============================================================================
-- Must run FIRST before any tables are created
-- Uses INCREMENT BY 50 for Hibernate pooled-lo optimizer
-- ============================================================================

-- liquibase formatted sql
-- changeset system:001-create-all-sequences

-- Legacy hibernate sequence (keep at 1 for compatibility)
CREATE SEQUENCE IF NOT EXISTS hibernate_sequence
    START WITH 1 INCREMENT BY 1 CACHE 1;

-- All entity sequences with INCREMENT BY 50 for pooled-lo optimizer
CREATE SEQUENCE IF NOT EXISTS user_seq START WITH 1 INCREMENT BY 50 CACHE 50;
CREATE SEQUENCE IF NOT EXISTS address_seq START WITH 1 INCREMENT BY 50 CACHE 50;
CREATE SEQUENCE IF NOT EXISTS applied_opportunity_seq START WITH 1 INCREMENT BY 50 CACHE 50;
CREATE SEQUENCE IF NOT EXISTS applied_opportunity_content_seq START WITH 1 INCREMENT BY 50 CACHE 50;
CREATE SEQUENCE IF NOT EXISTS applied_opportunity_status_history_seq START WITH 1 INCREMENT BY 50 CACHE 50;
CREATE SEQUENCE IF NOT EXISTS city_seq START WITH 1 INCREMENT BY 50 CACHE 50;
CREATE SEQUENCE IF NOT EXISTS content_type_seq START WITH 1 INCREMENT BY 50 CACHE 50;
CREATE SEQUENCE IF NOT EXISTS currency_seq START WITH 1 INCREMENT BY 50 CACHE 50;
CREATE SEQUENCE IF NOT EXISTS faq_seq START WITH 1 INCREMENT BY 50 CACHE 50;
CREATE SEQUENCE IF NOT EXISTS faq_category_seq START WITH 1 INCREMENT BY 50 CACHE 50;
CREATE SEQUENCE IF NOT EXISTS help_article_seq START WITH 1 INCREMENT BY 50 CACHE 50;
CREATE SEQUENCE IF NOT EXISTS help_article_category_seq START WITH 1 INCREMENT BY 50 CACHE 50;
CREATE SEQUENCE IF NOT EXISTS help_article_media_seq START WITH 1 INCREMENT BY 50 CACHE 50;
CREATE SEQUENCE IF NOT EXISTS help_article_tag_seq START WITH 1 INCREMENT BY 50 CACHE 50;
CREATE SEQUENCE IF NOT EXISTS partnership_opportunity_seq START WITH 1 INCREMENT BY 50 CACHE 50;
CREATE SEQUENCE IF NOT EXISTS partnership_opportunity_photo_seq START WITH 1 INCREMENT BY 50 CACHE 50;
CREATE SEQUENCE IF NOT EXISTS platform_seq START WITH 1 INCREMENT BY 50 CACHE 50;
CREATE SEQUENCE IF NOT EXISTS response_attachment_seq START WITH 1 INCREMENT BY 50 CACHE 50;
CREATE SEQUENCE IF NOT EXISTS service_type_seq START WITH 1 INCREMENT BY 50 CACHE 50;
CREATE SEQUENCE IF NOT EXISTS support_ticket_seq START WITH 1 INCREMENT BY 50 CACHE 50;
CREATE SEQUENCE IF NOT EXISTS ticket_attachment_seq START WITH 1 INCREMENT BY 50 CACHE 50;
CREATE SEQUENCE IF NOT EXISTS ticket_response_seq START WITH 1 INCREMENT BY 50 CACHE 50;
CREATE SEQUENCE IF NOT EXISTS user_preferences_seq START WITH 1 INCREMENT BY 50 CACHE 50;
CREATE SEQUENCE IF NOT EXISTS user_social_connection_seq START WITH 1 INCREMENT BY 50 CACHE 50;
CREATE SEQUENCE IF NOT EXISTS search_query_log_seq START WITH 1 INCREMENT BY 50 CACHE 50;

-- Consent management sequences (added 05-09-2025)
CREATE SEQUENCE IF NOT EXISTS consent_definition_seq START WITH 1 INCREMENT BY 50 CACHE 50;
CREATE SEQUENCE IF NOT EXISTS consent_version_seq START WITH 1 INCREMENT BY 50 CACHE 50;
CREATE SEQUENCE IF NOT EXISTS user_consent_seq START WITH 1 INCREMENT BY 50 CACHE 50;

-- rollback DROP SEQUENCE IF EXISTS hibernate_sequence;
-- rollback DROP SEQUENCE IF EXISTS user_seq;
-- rollback DROP SEQUENCE IF EXISTS address_seq;
-- rollback DROP SEQUENCE IF EXISTS applied_opportunity_seq;
-- rollback DROP SEQUENCE IF EXISTS applied_opportunity_content_seq;
-- rollback DROP SEQUENCE IF EXISTS applied_opportunity_status_history_seq;
-- rollback DROP SEQUENCE IF EXISTS city_seq;
-- rollback DROP SEQUENCE IF EXISTS content_type_seq;
-- rollback DROP SEQUENCE IF EXISTS currency_seq;
-- rollback DROP SEQUENCE IF EXISTS faq_seq;
-- rollback DROP SEQUENCE IF EXISTS faq_category_seq;
-- rollback DROP SEQUENCE IF EXISTS help_article_seq;
-- rollback DROP SEQUENCE IF EXISTS help_article_category_seq;
-- rollback DROP SEQUENCE IF EXISTS help_article_media_seq;
-- rollback DROP SEQUENCE IF EXISTS help_article_tag_seq;
-- rollback DROP SEQUENCE IF EXISTS partnership_opportunity_seq;
-- rollback DROP SEQUENCE IF EXISTS partnership_opportunity_photo_seq;
-- rollback DROP SEQUENCE IF EXISTS platform_seq;
-- rollback DROP SEQUENCE IF EXISTS response_attachment_seq;
-- rollback DROP SEQUENCE IF EXISTS service_type_seq;
-- rollback DROP SEQUENCE IF EXISTS support_ticket_seq;
-- rollback DROP SEQUENCE IF EXISTS ticket_attachment_seq;
-- rollback DROP SEQUENCE IF EXISTS ticket_response_seq;
-- rollback DROP SEQUENCE IF EXISTS user_preferences_seq;
-- rollback DROP SEQUENCE IF EXISTS user_social_connection_seq;
-- rollback DROP SEQUENCE IF EXISTS search_query_log_seq;
