-- changeset notification:2026-01-notification-translations

-- Clear existing notification translations (if any)
DELETE FROM dictionary_entries WHERE entry_key LIKE 'NOTIFICATION_%';

-- ============================================================
-- PARTNERSHIP NOTIFICATIONS
-- ============================================================

-- APPLICATION_RECEIVED (Company receives when influencer applies)
INSERT INTO dictionary_entries (entry_key, value, language_code, category, updater_id) VALUES
('NOTIFICATION_APPLICATION_RECEIVED_TITLE', 'New Application Received', 'en', 'notifications', 'system'),
('NOTIFICATION_APPLICATION_RECEIVED_MESSAGE', '{influencerName} has applied to your opportunity: {opportunityName}', 'en', 'notifications', 'system'),
('NOTIFICATION_APPLICATION_RECEIVED_ACTION', 'Review Application', 'en', 'notifications', 'system'),
('NOTIFICATION_APPLICATION_RECEIVED_TITLE', 'Nowa aplikacja', 'pl', 'notifications', 'system'),
('NOTIFICATION_APPLICATION_RECEIVED_MESSAGE', '{influencerName} aplikuje do Twojej oferty: {opportunityName}', 'pl', 'notifications', 'system'),
('NOTIFICATION_APPLICATION_RECEIVED_ACTION', 'Przejrzyj aplikację', 'pl', 'notifications', 'system');

-- APPLICATION_ACCEPTED (Influencer receives when company accepts)
INSERT INTO dictionary_entries (entry_key, value, language_code, category, updater_id) VALUES
('NOTIFICATION_APPLICATION_ACCEPTED_TITLE', 'Application Accepted!', 'en', 'notifications', 'system'),
('NOTIFICATION_APPLICATION_ACCEPTED_MESSAGE', '{companyName} has accepted your application for: {opportunityName}', 'en', 'notifications', 'system'),
('NOTIFICATION_APPLICATION_ACCEPTED_ACTION', 'View Offer', 'en', 'notifications', 'system'),
('NOTIFICATION_APPLICATION_ACCEPTED_TITLE', 'Aplikacja zaakceptowana!', 'pl', 'notifications', 'system'),
('NOTIFICATION_APPLICATION_ACCEPTED_MESSAGE', '{companyName} zaakceptowała Twoją aplikację: {opportunityName}', 'pl', 'notifications', 'system'),
('NOTIFICATION_APPLICATION_ACCEPTED_ACTION', 'Zobacz ofertę', 'pl', 'notifications', 'system');

-- APPLICATION_REJECTED (Influencer receives when company rejects)
INSERT INTO dictionary_entries (entry_key, value, language_code, category, updater_id) VALUES
('NOTIFICATION_APPLICATION_REJECTED_TITLE', 'Application Not Selected', 'en', 'notifications', 'system'),
('NOTIFICATION_APPLICATION_REJECTED_MESSAGE', '{companyName} has decided not to proceed with your application for: {opportunityName}', 'en', 'notifications', 'system'),
('NOTIFICATION_APPLICATION_REJECTED_ACTION', 'Find Other Opportunities', 'en', 'notifications', 'system'),
('NOTIFICATION_APPLICATION_REJECTED_TITLE', 'Aplikacja odrzucona', 'pl', 'notifications', 'system'),
('NOTIFICATION_APPLICATION_REJECTED_MESSAGE', '{companyName} nie kontynuuje współpracy dotyczącej: {opportunityName}', 'pl', 'notifications', 'system'),
('NOTIFICATION_APPLICATION_REJECTED_ACTION', 'Szukaj innych ofert', 'pl', 'notifications', 'system');

-- OFFER_ACCEPTED (Company receives when influencer confirms)
INSERT INTO dictionary_entries (entry_key, value, language_code, category, updater_id) VALUES
('NOTIFICATION_OFFER_ACCEPTED_TITLE', 'Influencer Confirmed!', 'en', 'notifications', 'system'),
('NOTIFICATION_OFFER_ACCEPTED_MESSAGE', '{influencerName} has confirmed the collaboration for: {opportunityName}', 'en', 'notifications', 'system'),
('NOTIFICATION_OFFER_ACCEPTED_ACTION', 'View Collaboration', 'en', 'notifications', 'system'),
('NOTIFICATION_OFFER_ACCEPTED_TITLE', 'Influencer potwierdził!', 'pl', 'notifications', 'system'),
('NOTIFICATION_OFFER_ACCEPTED_MESSAGE', '{influencerName} potwierdził współpracę: {opportunityName}', 'pl', 'notifications', 'system'),
('NOTIFICATION_OFFER_ACCEPTED_ACTION', 'Zobacz współpracę', 'pl', 'notifications', 'system');

-- OFFER_REJECTED (Company receives when influencer declines)
INSERT INTO dictionary_entries (entry_key, value, language_code, category, updater_id) VALUES
('NOTIFICATION_OFFER_REJECTED_TITLE', 'Influencer Declined', 'en', 'notifications', 'system'),
('NOTIFICATION_OFFER_REJECTED_MESSAGE', '{influencerName} has declined the collaboration for: {opportunityName}', 'en', 'notifications', 'system'),
('NOTIFICATION_OFFER_REJECTED_ACTION', 'Find Other Influencers', 'en', 'notifications', 'system'),
('NOTIFICATION_OFFER_REJECTED_TITLE', 'Influencer odmówił', 'pl', 'notifications', 'system'),
('NOTIFICATION_OFFER_REJECTED_MESSAGE', '{influencerName} odmówił współpracy: {opportunityName}', 'pl', 'notifications', 'system'),
('NOTIFICATION_OFFER_REJECTED_ACTION', 'Szukaj innych influencerów', 'pl', 'notifications', 'system');

-- CONTENT_SUBMITTED (Company receives when influencer submits content)
INSERT INTO dictionary_entries (entry_key, value, language_code, category, updater_id) VALUES
('NOTIFICATION_CONTENT_SUBMITTED_TITLE', 'Content Ready for Review', 'en', 'notifications', 'system'),
('NOTIFICATION_CONTENT_SUBMITTED_MESSAGE', '{influencerName} has submitted content for: {opportunityName}', 'en', 'notifications', 'system'),
('NOTIFICATION_CONTENT_SUBMITTED_ACTION', 'Review Content', 'en', 'notifications', 'system'),
('NOTIFICATION_CONTENT_SUBMITTED_TITLE', 'Treść do akceptacji', 'pl', 'notifications', 'system'),
('NOTIFICATION_CONTENT_SUBMITTED_MESSAGE', '{influencerName} przesłał treść do akceptacji: {opportunityName}', 'pl', 'notifications', 'system'),
('NOTIFICATION_CONTENT_SUBMITTED_ACTION', 'Przejrzyj treść', 'pl', 'notifications', 'system');

-- CONTENT_APPROVED (Influencer receives when company approves content)
INSERT INTO dictionary_entries (entry_key, value, language_code, category, updater_id) VALUES
('NOTIFICATION_CONTENT_APPROVED_TITLE', 'Content Approved!', 'en', 'notifications', 'system'),
('NOTIFICATION_CONTENT_APPROVED_MESSAGE', '{companyName} has approved your content for: {opportunityName}. You can now publish it!', 'en', 'notifications', 'system'),
('NOTIFICATION_CONTENT_APPROVED_ACTION', 'Publish Now', 'en', 'notifications', 'system'),
('NOTIFICATION_CONTENT_APPROVED_TITLE', 'Treść zatwierdzona!', 'pl', 'notifications', 'system'),
('NOTIFICATION_CONTENT_APPROVED_MESSAGE', '{companyName} zatwierdziła Twoją treść: {opportunityName}. Możesz ją opublikować!', 'pl', 'notifications', 'system'),
('NOTIFICATION_CONTENT_APPROVED_ACTION', 'Opublikuj teraz', 'pl', 'notifications', 'system');

-- CONTENT_REJECTED (Influencer receives when company requests changes)
INSERT INTO dictionary_entries (entry_key, value, language_code, category, updater_id) VALUES
('NOTIFICATION_CONTENT_REJECTED_TITLE', 'Content Needs Revision', 'en', 'notifications', 'system'),
('NOTIFICATION_CONTENT_REJECTED_MESSAGE', '{companyName} has requested changes to your content for: {opportunityName}', 'en', 'notifications', 'system'),
('NOTIFICATION_CONTENT_REJECTED_ACTION', 'View Feedback', 'en', 'notifications', 'system'),
('NOTIFICATION_CONTENT_REJECTED_TITLE', 'Treść wymaga poprawek', 'pl', 'notifications', 'system'),
('NOTIFICATION_CONTENT_REJECTED_MESSAGE', '{companyName} prosi o poprawki treści: {opportunityName}', 'pl', 'notifications', 'system'),
('NOTIFICATION_CONTENT_REJECTED_ACTION', 'Zobacz uwagi', 'pl', 'notifications', 'system');

-- CONTENT_POSTED (Company receives when influencer publishes)
INSERT INTO dictionary_entries (entry_key, value, language_code, category, updater_id) VALUES
('NOTIFICATION_CONTENT_POSTED_TITLE', 'Content Published', 'en', 'notifications', 'system'),
('NOTIFICATION_CONTENT_POSTED_MESSAGE', '{influencerName} has published the content for: {opportunityName}', 'en', 'notifications', 'system'),
('NOTIFICATION_CONTENT_POSTED_ACTION', 'Verify Post', 'en', 'notifications', 'system'),
('NOTIFICATION_CONTENT_POSTED_TITLE', 'Treść opublikowana', 'pl', 'notifications', 'system'),
('NOTIFICATION_CONTENT_POSTED_MESSAGE', '{influencerName} opublikował treść: {opportunityName}', 'pl', 'notifications', 'system'),
('NOTIFICATION_CONTENT_POSTED_ACTION', 'Zweryfikuj post', 'pl', 'notifications', 'system');

-- POST_VERIFIED (Influencer receives when post is verified)
INSERT INTO dictionary_entries (entry_key, value, language_code, category, updater_id) VALUES
('NOTIFICATION_POST_VERIFIED_TITLE', 'Post Verified - Payment Processing', 'en', 'notifications', 'system'),
('NOTIFICATION_POST_VERIFIED_MESSAGE', '{companyName} has verified your post for: {opportunityName}. Payment is being processed!', 'en', 'notifications', 'system'),
('NOTIFICATION_POST_VERIFIED_ACTION', 'View Payment Status', 'en', 'notifications', 'system'),
('NOTIFICATION_POST_VERIFIED_TITLE', 'Post zweryfikowany - płatność w toku', 'pl', 'notifications', 'system'),
('NOTIFICATION_POST_VERIFIED_MESSAGE', '{companyName} zweryfikowała Twój post: {opportunityName}. Płatność jest przetwarzana!', 'pl', 'notifications', 'system'),
('NOTIFICATION_POST_VERIFIED_ACTION', 'Status płatności', 'pl', 'notifications', 'system');

-- POST_REJECTED (Influencer receives when post is rejected)
INSERT INTO dictionary_entries (entry_key, value, language_code, category, updater_id) VALUES
('NOTIFICATION_POST_REJECTED_TITLE', 'Post Needs Correction', 'en', 'notifications', 'system'),
('NOTIFICATION_POST_REJECTED_MESSAGE', '{companyName} has found issues with your post for: {opportunityName}', 'en', 'notifications', 'system'),
('NOTIFICATION_POST_REJECTED_ACTION', 'View Issues', 'en', 'notifications', 'system'),
('NOTIFICATION_POST_REJECTED_TITLE', 'Post wymaga poprawek', 'pl', 'notifications', 'system'),
('NOTIFICATION_POST_REJECTED_MESSAGE', '{companyName} znalazła problemy z Twoim postem: {opportunityName}', 'pl', 'notifications', 'system'),
('NOTIFICATION_POST_REJECTED_ACTION', 'Zobacz problemy', 'pl', 'notifications', 'system');

-- COLLABORATION_COMPLETE (Both receive when done)
INSERT INTO dictionary_entries (entry_key, value, language_code, category, updater_id) VALUES
('NOTIFICATION_COLLABORATION_COMPLETE_TITLE', 'Collaboration Complete!', 'en', 'notifications', 'system'),
('NOTIFICATION_COLLABORATION_COMPLETE_MESSAGE', 'The collaboration for {opportunityName} has been successfully completed!', 'en', 'notifications', 'system'),
('NOTIFICATION_COLLABORATION_COMPLETE_ACTION', 'Rate Partner', 'en', 'notifications', 'system'),
('NOTIFICATION_COLLABORATION_COMPLETE_TITLE', 'Współpraca zakończona!', 'pl', 'notifications', 'system'),
('NOTIFICATION_COLLABORATION_COMPLETE_MESSAGE', 'Współpraca przy {opportunityName} została pomyślnie zakończona!', 'pl', 'notifications', 'system'),
('NOTIFICATION_COLLABORATION_COMPLETE_ACTION', 'Oceń partnera', 'pl', 'notifications', 'system');

-- ============================================================
-- ACCOUNT NOTIFICATIONS
-- ============================================================

-- ACCOUNT_ACTIVATED
INSERT INTO dictionary_entries (entry_key, value, language_code, category, updater_id) VALUES
('NOTIFICATION_ACCOUNT_ACTIVATED_TITLE', 'Account Activated!', 'en', 'notifications', 'system'),
('NOTIFICATION_ACCOUNT_ACTIVATED_MESSAGE', 'Your CheckItOut account has been activated. You can now access all features!', 'en', 'notifications', 'system'),
('NOTIFICATION_ACCOUNT_ACTIVATED_ACTION', 'Explore Platform', 'en', 'notifications', 'system'),
('NOTIFICATION_ACCOUNT_ACTIVATED_TITLE', 'Konto aktywowane!', 'pl', 'notifications', 'system'),
('NOTIFICATION_ACCOUNT_ACTIVATED_MESSAGE', 'Twoje konto CheckItOut zostało aktywowane. Możesz korzystać ze wszystkich funkcji!', 'pl', 'notifications', 'system'),
('NOTIFICATION_ACCOUNT_ACTIVATED_ACTION', 'Odkryj platformę', 'pl', 'notifications', 'system');

-- ACCOUNT_SUSPENDED
INSERT INTO dictionary_entries (entry_key, value, language_code, category, updater_id) VALUES
('NOTIFICATION_ACCOUNT_SUSPENDED_TITLE', 'Account Suspended', 'en', 'notifications', 'system'),
('NOTIFICATION_ACCOUNT_SUSPENDED_MESSAGE', 'Your account has been temporarily suspended. Please contact support for more information.', 'en', 'notifications', 'system'),
('NOTIFICATION_ACCOUNT_SUSPENDED_ACTION', 'Contact Support', 'en', 'notifications', 'system'),
('NOTIFICATION_ACCOUNT_SUSPENDED_TITLE', 'Konto zawieszone', 'pl', 'notifications', 'system'),
('NOTIFICATION_ACCOUNT_SUSPENDED_MESSAGE', 'Twoje konto zostało tymczasowo zawieszone. Skontaktuj się z pomocą techniczną.', 'pl', 'notifications', 'system'),
('NOTIFICATION_ACCOUNT_SUSPENDED_ACTION', 'Kontakt z pomocą', 'pl', 'notifications', 'system');

-- ACCOUNT_BANNED
INSERT INTO dictionary_entries (entry_key, value, language_code, category, updater_id) VALUES
('NOTIFICATION_ACCOUNT_BANNED_TITLE', 'Account Permanently Disabled', 'en', 'notifications', 'system'),
('NOTIFICATION_ACCOUNT_BANNED_MESSAGE', 'Your account has been permanently disabled due to terms of service violations.', 'en', 'notifications', 'system'),
('NOTIFICATION_ACCOUNT_BANNED_ACTION', 'Appeal Decision', 'en', 'notifications', 'system'),
('NOTIFICATION_ACCOUNT_BANNED_TITLE', 'Konto zablokowane', 'pl', 'notifications', 'system'),
('NOTIFICATION_ACCOUNT_BANNED_MESSAGE', 'Twoje konto zostało trwale zablokowane z powodu naruszenia regulaminu.', 'pl', 'notifications', 'system'),
('NOTIFICATION_ACCOUNT_BANNED_ACTION', 'Odwołaj się', 'pl', 'notifications', 'system');

-- ============================================================
-- SUPPORT NOTIFICATIONS
-- ============================================================

-- TICKET_RESPONSE
INSERT INTO dictionary_entries (entry_key, value, language_code, category, updater_id) VALUES
('NOTIFICATION_TICKET_RESPONSE_TITLE', 'New Support Response', 'en', 'notifications', 'system'),
('NOTIFICATION_TICKET_RESPONSE_MESSAGE', 'Our support team has responded to your ticket: {ticketSubject}', 'en', 'notifications', 'system'),
('NOTIFICATION_TICKET_RESPONSE_ACTION', 'View Response', 'en', 'notifications', 'system'),
('NOTIFICATION_TICKET_RESPONSE_TITLE', 'Nowa odpowiedź', 'pl', 'notifications', 'system'),
('NOTIFICATION_TICKET_RESPONSE_MESSAGE', 'Nasz zespół odpowiedział na Twoje zgłoszenie: {ticketSubject}', 'pl', 'notifications', 'system'),
('NOTIFICATION_TICKET_RESPONSE_ACTION', 'Zobacz odpowiedź', 'pl', 'notifications', 'system');

-- TICKET_RESOLVED
INSERT INTO dictionary_entries (entry_key, value, language_code, category, updater_id) VALUES
('NOTIFICATION_TICKET_RESOLVED_TITLE', 'Ticket Resolved', 'en', 'notifications', 'system'),
('NOTIFICATION_TICKET_RESOLVED_MESSAGE', 'Your support ticket "{ticketSubject}" has been marked as resolved.', 'en', 'notifications', 'system'),
('NOTIFICATION_TICKET_RESOLVED_ACTION', 'Rate Support', 'en', 'notifications', 'system'),
('NOTIFICATION_TICKET_RESOLVED_TITLE', 'Zgłoszenie rozwiązane', 'pl', 'notifications', 'system'),
('NOTIFICATION_TICKET_RESOLVED_MESSAGE', 'Twoje zgłoszenie "{ticketSubject}" zostało rozwiązane.', 'pl', 'notifications', 'system'),
('NOTIFICATION_TICKET_RESOLVED_ACTION', 'Oceń pomoc', 'pl', 'notifications', 'system');

-- TICKET_CLOSED
INSERT INTO dictionary_entries (entry_key, value, language_code, category, updater_id) VALUES
('NOTIFICATION_TICKET_CLOSED_TITLE', 'Ticket Closed', 'en', 'notifications', 'system'),
('NOTIFICATION_TICKET_CLOSED_MESSAGE', 'Your support ticket "{ticketSubject}" has been closed.', 'en', 'notifications', 'system'),
('NOTIFICATION_TICKET_CLOSED_ACTION', 'Reopen if Needed', 'en', 'notifications', 'system'),
('NOTIFICATION_TICKET_CLOSED_TITLE', 'Zgłoszenie zamknięte', 'pl', 'notifications', 'system'),
('NOTIFICATION_TICKET_CLOSED_MESSAGE', 'Twoje zgłoszenie "{ticketSubject}" zostało zamknięte.', 'pl', 'notifications', 'system'),
('NOTIFICATION_TICKET_CLOSED_ACTION', 'Otwórz ponownie', 'pl', 'notifications', 'system')

ON CONFLICT (entry_key, language_code) DO UPDATE SET
    value = EXCLUDED.value,
    updated_at = CURRENT_TIMESTAMP;

-- rollback DELETE FROM dictionary_entries WHERE category = 'notifications';