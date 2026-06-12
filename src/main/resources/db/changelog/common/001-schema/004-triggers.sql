-- ============================================================================
-- 004-TRIGGERS: CREATE ALL DATABASE TRIGGERS
-- ============================================================================
-- Automatic timestamp management for created_time and last_update_time
-- ============================================================================

-- liquibase formatted sql
-- changeset system:004-create-all-triggers

-- ============================================================================
-- TRIGGER FUNCTIONS
-- ============================================================================

-- Function to set created_time on INSERT
CREATE OR REPLACE FUNCTION set_created_time()
RETURNS TRIGGER AS $$
BEGIN
    NEW.created_time = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Function to update last_update_time on UPDATE
CREATE OR REPLACE FUNCTION update_last_update_time()
RETURNS TRIGGER AS $$
BEGIN
    NEW.last_update_time = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ============================================================================
-- CREATE TIME TRIGGERS (for tables with created_time)
-- ============================================================================

CREATE TRIGGER set_created_time_user
BEFORE INSERT ON "user"
FOR EACH ROW
EXECUTE FUNCTION set_created_time();

CREATE TRIGGER set_created_time_partnership_opportunity
BEFORE INSERT ON partnership_opportunity
FOR EACH ROW
EXECUTE FUNCTION set_created_time();

CREATE TRIGGER set_created_time_applied_opportunity
BEFORE INSERT ON applied_opportunity
FOR EACH ROW
EXECUTE FUNCTION set_created_time();

CREATE TRIGGER set_created_time_user_preferences
BEFORE INSERT ON user_preferences
FOR EACH ROW
EXECUTE FUNCTION set_created_time();

CREATE TRIGGER set_created_time_address
BEFORE INSERT ON address
FOR EACH ROW
EXECUTE FUNCTION set_created_time();

CREATE TRIGGER set_created_time_user_social_connection
BEFORE INSERT ON user_social_connection
FOR EACH ROW
EXECUTE FUNCTION set_created_time();

CREATE TRIGGER set_created_time_support_ticket
BEFORE INSERT ON support_ticket
FOR EACH ROW
EXECUTE FUNCTION set_created_time();

CREATE TRIGGER set_created_time_ticket_response
BEFORE INSERT ON ticket_response
FOR EACH ROW
EXECUTE FUNCTION set_created_time();

CREATE TRIGGER set_created_time_help_article_category
BEFORE INSERT ON help_article_category
FOR EACH ROW
EXECUTE FUNCTION set_created_time();

CREATE TRIGGER set_created_time_help_article
BEFORE INSERT ON help_article
FOR EACH ROW
EXECUTE FUNCTION set_created_time();

CREATE TRIGGER set_created_time_help_article_tag
BEFORE INSERT ON help_article_tag
FOR EACH ROW
EXECUTE FUNCTION set_created_time();

CREATE TRIGGER set_created_time_faq_category
BEFORE INSERT ON faq_category
FOR EACH ROW
EXECUTE FUNCTION set_created_time();

CREATE TRIGGER set_created_time_faq
BEFORE INSERT ON faq
FOR EACH ROW
EXECUTE FUNCTION set_created_time();

CREATE TRIGGER set_created_time_applied_opportunity_content
BEFORE INSERT ON applied_opportunity_content
FOR EACH ROW
EXECUTE FUNCTION set_created_time();

-- ============================================================================
-- UPDATE TIME TRIGGERS (for tables with last_update_time)
-- ============================================================================

CREATE TRIGGER set_last_update_time_user
BEFORE UPDATE ON "user"
FOR EACH ROW
EXECUTE FUNCTION update_last_update_time();

CREATE TRIGGER set_last_update_time_partnership_opportunity
BEFORE UPDATE ON partnership_opportunity
FOR EACH ROW
EXECUTE FUNCTION update_last_update_time();

CREATE TRIGGER set_last_update_time_applied_opportunity
BEFORE UPDATE ON applied_opportunity
FOR EACH ROW
EXECUTE FUNCTION update_last_update_time();

CREATE TRIGGER set_last_update_time_user_preferences
BEFORE UPDATE ON user_preferences
FOR EACH ROW
EXECUTE FUNCTION update_last_update_time();

CREATE TRIGGER set_last_update_time_address
BEFORE UPDATE ON address
FOR EACH ROW
EXECUTE FUNCTION update_last_update_time();

CREATE TRIGGER set_last_update_time_user_social_connection
BEFORE UPDATE ON user_social_connection
FOR EACH ROW
EXECUTE FUNCTION update_last_update_time();

CREATE TRIGGER set_last_update_time_support_ticket
BEFORE UPDATE ON support_ticket
FOR EACH ROW
EXECUTE FUNCTION update_last_update_time();

CREATE TRIGGER set_last_update_time_help_article_category
BEFORE UPDATE ON help_article_category
FOR EACH ROW
EXECUTE FUNCTION update_last_update_time();

CREATE TRIGGER set_last_update_time_help_article
BEFORE UPDATE ON help_article
FOR EACH ROW
EXECUTE FUNCTION update_last_update_time();

CREATE TRIGGER set_last_update_time_faq_category
BEFORE UPDATE ON faq_category
FOR EACH ROW
EXECUTE FUNCTION update_last_update_time();

CREATE TRIGGER set_last_update_time_faq
BEFORE UPDATE ON faq
FOR EACH ROW
EXECUTE FUNCTION update_last_update_time();

CREATE TRIGGER set_last_update_time_applied_opportunity_content
BEFORE UPDATE ON applied_opportunity_content
FOR EACH ROW
EXECUTE FUNCTION update_last_update_time();

CREATE TRIGGER set_last_update_time_dictionary_entries
BEFORE UPDATE ON dictionary_entries
FOR EACH ROW
EXECUTE FUNCTION update_last_update_time();

-- rollback DROP TRIGGER IF EXISTS set_created_time_user ON "user";
-- rollback DROP TRIGGER IF EXISTS set_created_time_partnership_opportunity ON partnership_opportunity;
-- rollback DROP TRIGGER IF EXISTS set_created_time_applied_opportunity ON applied_opportunity;
-- rollback DROP TRIGGER IF EXISTS set_created_time_user_preferences ON user_preferences;
-- rollback DROP TRIGGER IF EXISTS set_created_time_address ON address;
-- rollback DROP TRIGGER IF EXISTS set_created_time_user_social_connection ON user_social_connection;
-- rollback DROP TRIGGER IF EXISTS set_created_time_support_ticket ON support_ticket;
-- rollback DROP TRIGGER IF EXISTS set_created_time_ticket_response ON ticket_response;
-- rollback DROP TRIGGER IF EXISTS set_created_time_help_article_category ON help_article_category;
-- rollback DROP TRIGGER IF EXISTS set_created_time_help_article ON help_article;
-- rollback DROP TRIGGER IF EXISTS set_created_time_help_article_tag ON help_article_tag;
-- rollback DROP TRIGGER IF EXISTS set_created_time_faq_category ON faq_category;
-- rollback DROP TRIGGER IF EXISTS set_created_time_faq ON faq;
-- rollback DROP TRIGGER IF EXISTS set_created_time_applied_opportunity_content ON applied_opportunity_content;
-- rollback DROP TRIGGER IF EXISTS set_last_update_time_user ON "user";
-- rollback DROP TRIGGER IF EXISTS set_last_update_time_partnership_opportunity ON partnership_opportunity;
-- rollback DROP TRIGGER IF EXISTS set_last_update_time_applied_opportunity ON applied_opportunity;
-- rollback DROP TRIGGER IF EXISTS set_last_update_time_user_preferences ON user_preferences;
-- rollback DROP TRIGGER IF EXISTS set_last_update_time_address ON address;
-- rollback DROP TRIGGER IF EXISTS set_last_update_time_user_social_connection ON user_social_connection;
-- rollback DROP TRIGGER IF EXISTS set_last_update_time_support_ticket ON support_ticket;
-- rollback DROP TRIGGER IF EXISTS set_last_update_time_help_article_category ON help_article_category;
-- rollback DROP TRIGGER IF EXISTS set_last_update_time_help_article ON help_article;
-- rollback DROP TRIGGER IF EXISTS set_last_update_time_faq_category ON faq_category;
-- rollback DROP TRIGGER IF EXISTS set_last_update_time_faq ON faq;
-- rollback DROP TRIGGER IF EXISTS set_last_update_time_applied_opportunity_content ON applied_opportunity_content;
-- rollback DROP TRIGGER IF EXISTS set_last_update_time_dictionary_entries ON dictionary_entries;
-- rollback DROP FUNCTION IF EXISTS set_created_time();
-- rollback DROP FUNCTION IF EXISTS update_last_update_time();
