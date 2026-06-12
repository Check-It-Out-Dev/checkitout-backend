-- changeset notification:22-03-2026-admin-notification-translations

-- Admin notification translations for ADMIN_NEW_USER_REGISTERED and ADMIN_ACCOUNT_ACTIVATED

-- ADMIN_NEW_USER_REGISTERED
INSERT INTO dictionary_entries (entry_key, value, language_code, category, updater_id) VALUES
('NOTIFICATION_ADMIN_NEW_USER_REGISTERED_TITLE', 'New User Registered', 'en', 'notifications', 'system'),
('NOTIFICATION_ADMIN_NEW_USER_REGISTERED_MESSAGE', '{userName} ({userEmail}) has registered as {userType}', 'en', 'notifications', 'system'),
('NOTIFICATION_ADMIN_NEW_USER_REGISTERED_ACTION', 'View Users', 'en', 'notifications', 'system'),
('NOTIFICATION_ADMIN_NEW_USER_REGISTERED_TITLE', 'Nowy uzytkownik', 'pl', 'notifications', 'system'),
('NOTIFICATION_ADMIN_NEW_USER_REGISTERED_MESSAGE', '{userName} ({userEmail}) zarejestrowal sie jako {userType}', 'pl', 'notifications', 'system'),
('NOTIFICATION_ADMIN_NEW_USER_REGISTERED_ACTION', 'Zobacz uzytkownikow', 'pl', 'notifications', 'system');

-- ADMIN_ACCOUNT_ACTIVATED
INSERT INTO dictionary_entries (entry_key, value, language_code, category, updater_id) VALUES
('NOTIFICATION_ADMIN_ACCOUNT_ACTIVATED_TITLE', 'Account Activated', 'en', 'notifications', 'system'),
('NOTIFICATION_ADMIN_ACCOUNT_ACTIVATED_MESSAGE', '{userName} ({userType}) account has been activated via {activationSource}', 'en', 'notifications', 'system'),
('NOTIFICATION_ADMIN_ACCOUNT_ACTIVATED_ACTION', 'View Users', 'en', 'notifications', 'system'),
('NOTIFICATION_ADMIN_ACCOUNT_ACTIVATED_TITLE', 'Konto aktywowane', 'pl', 'notifications', 'system'),
('NOTIFICATION_ADMIN_ACCOUNT_ACTIVATED_MESSAGE', 'Konto {userName} ({userType}) zostalo aktywowane przez {activationSource}', 'pl', 'notifications', 'system'),
('NOTIFICATION_ADMIN_ACCOUNT_ACTIVATED_ACTION', 'Zobacz uzytkownikow', 'pl', 'notifications', 'system');
