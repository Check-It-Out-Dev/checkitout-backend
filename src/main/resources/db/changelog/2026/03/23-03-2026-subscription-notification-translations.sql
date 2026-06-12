-- liquibase formatted sql
-- changeset system:subscription-notification-translations

-- ============================================================
-- SUBSCRIPTION NOTIFICATIONS (10 types x 3 keys x 2 languages)
-- ============================================================

INSERT INTO dictionary_entries (entry_key, value, language_code, category, updater_id) VALUES
('NOTIFICATION_SUBSCRIPTION_TRIAL_ENDING_TITLE', 'Trial Ending Soon', 'en', 'notifications', 'system'),
('NOTIFICATION_SUBSCRIPTION_TRIAL_ENDING_MESSAGE', 'Your Enterprise trial ends in {daysRemaining} days. Upgrade now to keep your features!', 'en', 'notifications', 'system'),
('NOTIFICATION_SUBSCRIPTION_TRIAL_ENDING_ACTION', 'View Plans', 'en', 'notifications', 'system'),
('NOTIFICATION_SUBSCRIPTION_TRIAL_ENDING_TITLE', 'Okres próbny dobiega końca', 'pl', 'notifications', 'system'),
('NOTIFICATION_SUBSCRIPTION_TRIAL_ENDING_MESSAGE', 'Twój okres próbny Enterprise kończy się za {daysRemaining} dni. Wybierz plan!', 'pl', 'notifications', 'system'),
('NOTIFICATION_SUBSCRIPTION_TRIAL_ENDING_ACTION', 'Zobacz plany', 'pl', 'notifications', 'system'),

('NOTIFICATION_SUBSCRIPTION_TRIAL_EXPIRED_TITLE', 'Trial Has Expired', 'en', 'notifications', 'system'),
('NOTIFICATION_SUBSCRIPTION_TRIAL_EXPIRED_MESSAGE', 'Your Enterprise trial has ended. Your account is now on the Free plan.', 'en', 'notifications', 'system'),
('NOTIFICATION_SUBSCRIPTION_TRIAL_EXPIRED_ACTION', 'Upgrade Now', 'en', 'notifications', 'system'),
('NOTIFICATION_SUBSCRIPTION_TRIAL_EXPIRED_TITLE', 'Okres próbny wygasł', 'pl', 'notifications', 'system'),
('NOTIFICATION_SUBSCRIPTION_TRIAL_EXPIRED_MESSAGE', 'Twój okres próbny Enterprise zakończył się. Konto jest na planie Darmowym.', 'pl', 'notifications', 'system'),
('NOTIFICATION_SUBSCRIPTION_TRIAL_EXPIRED_ACTION', 'Wybierz plan', 'pl', 'notifications', 'system'),

('NOTIFICATION_SUBSCRIPTION_PAYMENT_FAILED_TITLE', 'Payment Failed', 'en', 'notifications', 'system'),
('NOTIFICATION_SUBSCRIPTION_PAYMENT_FAILED_MESSAGE', 'We could not process payment for your {planName} plan. Please update your payment method.', 'en', 'notifications', 'system'),
('NOTIFICATION_SUBSCRIPTION_PAYMENT_FAILED_ACTION', 'Update Payment', 'en', 'notifications', 'system'),
('NOTIFICATION_SUBSCRIPTION_PAYMENT_FAILED_TITLE', 'Płatność nieudana', 'pl', 'notifications', 'system'),
('NOTIFICATION_SUBSCRIPTION_PAYMENT_FAILED_MESSAGE', 'Nie udało się pobrać płatności za plan {planName}. Zaktualizuj metodę płatności.', 'pl', 'notifications', 'system'),
('NOTIFICATION_SUBSCRIPTION_PAYMENT_FAILED_ACTION', 'Zaktualizuj płatność', 'pl', 'notifications', 'system'),

('NOTIFICATION_SUBSCRIPTION_PAYMENT_RECOVERED_TITLE', 'Payment Successful', 'en', 'notifications', 'system'),
('NOTIFICATION_SUBSCRIPTION_PAYMENT_RECOVERED_MESSAGE', 'Your {planName} payment has been processed. Your subscription is active!', 'en', 'notifications', 'system'),
('NOTIFICATION_SUBSCRIPTION_PAYMENT_RECOVERED_ACTION', 'View Subscription', 'en', 'notifications', 'system'),
('NOTIFICATION_SUBSCRIPTION_PAYMENT_RECOVERED_TITLE', 'Płatność zrealizowana', 'pl', 'notifications', 'system'),
('NOTIFICATION_SUBSCRIPTION_PAYMENT_RECOVERED_MESSAGE', 'Płatność za plan {planName} została zrealizowana. Subskrypcja aktywna!', 'pl', 'notifications', 'system'),
('NOTIFICATION_SUBSCRIPTION_PAYMENT_RECOVERED_ACTION', 'Zobacz subskrypcję', 'pl', 'notifications', 'system'),

('NOTIFICATION_SUBSCRIPTION_PAYMENT_EXHAUSTED_TITLE', 'Subscription Cancelled', 'en', 'notifications', 'system'),
('NOTIFICATION_SUBSCRIPTION_PAYMENT_EXHAUSTED_MESSAGE', 'All payment attempts for {oldPlan} failed. Your account is now on the Free plan.', 'en', 'notifications', 'system'),
('NOTIFICATION_SUBSCRIPTION_PAYMENT_EXHAUSTED_ACTION', 'Resubscribe', 'en', 'notifications', 'system'),
('NOTIFICATION_SUBSCRIPTION_PAYMENT_EXHAUSTED_TITLE', 'Subskrypcja anulowana', 'pl', 'notifications', 'system'),
('NOTIFICATION_SUBSCRIPTION_PAYMENT_EXHAUSTED_MESSAGE', 'Wszystkie próby płatności za {oldPlan} nie powiodły się. Konto na planie Darmowym.', 'pl', 'notifications', 'system'),
('NOTIFICATION_SUBSCRIPTION_PAYMENT_EXHAUSTED_ACTION', 'Ponów subskrypcję', 'pl', 'notifications', 'system'),

('NOTIFICATION_SUBSCRIPTION_UPGRADED_TITLE', 'Plan Upgraded!', 'en', 'notifications', 'system'),
('NOTIFICATION_SUBSCRIPTION_UPGRADED_MESSAGE', 'You have been upgraded to the {planName} plan!', 'en', 'notifications', 'system'),
('NOTIFICATION_SUBSCRIPTION_UPGRADED_ACTION', 'Explore Features', 'en', 'notifications', 'system'),
('NOTIFICATION_SUBSCRIPTION_UPGRADED_TITLE', 'Plan ulepszony!', 'pl', 'notifications', 'system'),
('NOTIFICATION_SUBSCRIPTION_UPGRADED_MESSAGE', 'Twój plan został ulepszony do {planName}!', 'pl', 'notifications', 'system'),
('NOTIFICATION_SUBSCRIPTION_UPGRADED_ACTION', 'Odkryj funkcje', 'pl', 'notifications', 'system'),

('NOTIFICATION_SUBSCRIPTION_DOWNGRADE_SCHEDULED_TITLE', 'Downgrade Scheduled', 'en', 'notifications', 'system'),
('NOTIFICATION_SUBSCRIPTION_DOWNGRADE_SCHEDULED_MESSAGE', 'Your plan will change from {currentPlan} to {targetPlan} at the end of the billing period.', 'en', 'notifications', 'system'),
('NOTIFICATION_SUBSCRIPTION_DOWNGRADE_SCHEDULED_ACTION', 'View Subscription', 'en', 'notifications', 'system'),
('NOTIFICATION_SUBSCRIPTION_DOWNGRADE_SCHEDULED_TITLE', 'Zaplanowano zmianę planu', 'pl', 'notifications', 'system'),
('NOTIFICATION_SUBSCRIPTION_DOWNGRADE_SCHEDULED_MESSAGE', 'Twój plan zmieni się z {currentPlan} na {targetPlan} na koniec okresu rozliczeniowego.', 'pl', 'notifications', 'system'),
('NOTIFICATION_SUBSCRIPTION_DOWNGRADE_SCHEDULED_ACTION', 'Zobacz subskrypcję', 'pl', 'notifications', 'system'),

('NOTIFICATION_SUBSCRIPTION_DOWNGRADED_TITLE', 'Plan Changed', 'en', 'notifications', 'system'),
('NOTIFICATION_SUBSCRIPTION_DOWNGRADED_MESSAGE', 'Your plan has been changed from {oldPlan} to {newPlan}.', 'en', 'notifications', 'system'),
('NOTIFICATION_SUBSCRIPTION_DOWNGRADED_ACTION', 'View Subscription', 'en', 'notifications', 'system'),
('NOTIFICATION_SUBSCRIPTION_DOWNGRADED_TITLE', 'Plan zmieniony', 'pl', 'notifications', 'system'),
('NOTIFICATION_SUBSCRIPTION_DOWNGRADED_MESSAGE', 'Twój plan został zmieniony z {oldPlan} na {newPlan}.', 'pl', 'notifications', 'system'),
('NOTIFICATION_SUBSCRIPTION_DOWNGRADED_ACTION', 'Zobacz subskrypcję', 'pl', 'notifications', 'system'),

('NOTIFICATION_SUBSCRIPTION_SUSPENDED_TITLE', 'Account Suspended', 'en', 'notifications', 'system'),
('NOTIFICATION_SUBSCRIPTION_SUSPENDED_MESSAGE', 'Your account has been suspended due to unaccepted terms. Accept the new terms to restore access.', 'en', 'notifications', 'system'),
('NOTIFICATION_SUBSCRIPTION_SUSPENDED_ACTION', 'Accept Terms', 'en', 'notifications', 'system'),
('NOTIFICATION_SUBSCRIPTION_SUSPENDED_TITLE', 'Konto zawieszone', 'pl', 'notifications', 'system'),
('NOTIFICATION_SUBSCRIPTION_SUSPENDED_MESSAGE', 'Konto zostało zawieszone z powodu niezaakceptowania regulaminu. Zaakceptuj nowy regulamin.', 'pl', 'notifications', 'system'),
('NOTIFICATION_SUBSCRIPTION_SUSPENDED_ACTION', 'Zaakceptuj regulamin', 'pl', 'notifications', 'system'),

('NOTIFICATION_SUBSCRIPTION_REACTIVATED_TITLE', 'Account Reactivated', 'en', 'notifications', 'system'),
('NOTIFICATION_SUBSCRIPTION_REACTIVATED_MESSAGE', 'Your {planName} subscription has been reactivated. Welcome back!', 'en', 'notifications', 'system'),
('NOTIFICATION_SUBSCRIPTION_REACTIVATED_ACTION', 'View Subscription', 'en', 'notifications', 'system'),
('NOTIFICATION_SUBSCRIPTION_REACTIVATED_TITLE', 'Konto reaktywowane', 'pl', 'notifications', 'system'),
('NOTIFICATION_SUBSCRIPTION_REACTIVATED_MESSAGE', 'Twoja subskrypcja {planName} została reaktywowana. Witamy ponownie!', 'pl', 'notifications', 'system'),
('NOTIFICATION_SUBSCRIPTION_REACTIVATED_ACTION', 'Zobacz subskrypcję', 'pl', 'notifications', 'system')

ON CONFLICT (entry_key, language_code) DO UPDATE SET value = EXCLUDED.value;

-- rollback DELETE FROM dictionary_entries WHERE entry_key LIKE 'NOTIFICATION_SUBSCRIPTION_%';
