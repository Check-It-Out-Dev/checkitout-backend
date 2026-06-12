-- liquibase formatted sql
-- changeset system:consent-initial-data
-- Insert initial consent definitions and versions

-- Insert basic consent types
INSERT INTO public.consent_definition (consent_type, name, description, regulation_reference) VALUES
('marketing', 'Marketing Communications', 'Consent to receive marketing emails and promotional content', 'GDPR Article 6(1)(a)'),
('analytics', 'Analytics and Performance', 'Consent to collect analytics data to improve our services', 'GDPR Article 6(1)(a)'),
('cookies', 'Non-Essential Cookies', 'Consent to use non-essential cookies for enhanced user experience', 'GDPR Article 6(1)(a)');

-- Insert initial versions for each consent type
INSERT INTO public.consent_version (consent_definition_id, version, consent_text, effective_from)
SELECT
    cd.id,
    '1.0',
    CASE
        WHEN cd.consent_type = 'marketing' THEN 'I consent to receive marketing communications including newsletters, promotional offers, and updates about new features via email.'
        WHEN cd.consent_type = 'analytics' THEN 'I consent to the collection and analysis of usage data to help improve the platform''s functionality and user experience.'
        WHEN cd.consent_type = 'cookies' THEN 'I consent to the use of non-essential cookies to enhance my browsing experience and provide personalized content.'
    END,
    CURRENT_TIMESTAMP
FROM consent_definition cd;

-- rollback DELETE FROM consent_version WHERE version = '1.0';
-- rollback DELETE FROM consent_definition WHERE consent_type IN ('marketing', 'analytics', 'cookies');
