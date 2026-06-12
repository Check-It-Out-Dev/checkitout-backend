package com.sm.instagram.platform.auth.service;

import com.sm.instagram.platform.auth.dto.MetaCallbackPayload;
import com.sm.instagram.platform.auth.firebase.FirestoreService;
import com.sm.instagram.platform.support.common.EmailService;
import com.sm.instagram.platform.user.User;
import com.sm.instagram.platform.user.UserAccountOrchestrator;
import com.sm.instagram.platform.usersocialconnection.ConnectionStatus;
import com.sm.instagram.platform.usersocialconnection.UserSocialConnection;
import com.sm.instagram.platform.usersocialconnection.UserSocialConnectionRepository;
import com.sm.instagram.platform.userpreferences.UserPreferences;
import com.sm.instagram.platform.userpreferences.UserPreferencesRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles Instagram deauthorization callbacks from Meta.
 * <p>
 * Two branches based on user state:
 * - Branch A (inactive, no verified email): archive/anonymize the user entirely
 * - Branch B (active, verified email): disconnect Instagram, send notification email
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InstagramDeauthorizationService {

    private final MetaSignedRequestService metaSignedRequestService;
    private final UserSocialConnectionRepository socialConnectionRepository;
    private final UserAccountOrchestrator userAccountOrchestrator;
    private final FirestoreService firestoreService;
    private final EmailService emailService;
    private final UserPreferencesRepository userPreferencesRepository;

    /**
     * Process a Meta deauthorization callback.
     * Always completes without throwing — Meta requires HTTP 200.
     *
     * @param signedRequest the raw signed_request from Meta
     */
    @Transactional
    public void processDeauthorization(String signedRequest) {
        MetaCallbackPayload payload = metaSignedRequestService.parseSignedRequest(signedRequest);

        log.info("GDPR: Processing Instagram deauthorization for social user ID: {}", payload.userId());

        var connectionOpt = socialConnectionRepository.findByPlatform_NameAndSocialUserId(
                "Instagram", payload.userId());

        if (connectionOpt.isEmpty()) {
            log.warn("GDPR: Deauthorization callback: no social connection found for Instagram user ID: {}", payload.userId());
            return;
        }

        UserSocialConnection connection = connectionOpt.get();
        User user = connection.getUser();

        // Already disconnected or user already archived — skip
        if (connection.getConnectionStatus() == ConnectionStatus.DISCONNECTED) {
            log.info("GDPR: Deauthorization callback: connection already DISCONNECTED for user {}", user.getId());
            return;
        }

        if (Boolean.TRUE.equals(user.getEmailVerified())) {
            processActiveUser(connection, user);
        } else {
            processInactiveUser(connection, user);
        }
    }

    /**
     * Branch B: Active user with verified email.
     * Disconnect Instagram, delete Firestore data, send notification email.
     */
    private void processActiveUser(UserSocialConnection connection, User user) {
        log.info("GDPR: Deauthorization Branch B (active user): userId={}, email verified", user.getId());

        connection.setConnectionStatus(ConnectionStatus.DISCONNECTED);
        socialConnectionRepository.save(connection);

        // Delete Firestore token data (non-fatal if it fails)
        try {
            firestoreService.deleteInstagramUserData(user.getFirebaseUserId());
        } catch (Exception e) {
            log.error("GDPR: Failed to delete Firestore data for user {}: {}", user.getFirebaseUserId(), e.getMessage());
        }

        // Send notification email (async, fire-and-forget)
        String language = getUserLanguage(user);
        emailService.sendDeauthorizationNotice(
                user.getEmail(),
                user.getFirstName(),
                language
        );

        log.info("GDPR: Active user {} Instagram connection deauthorized, notification sent", user.getId());
    }

    /**
     * Branch A: Inactive user without verified email.
     * Archive/anonymize the user entirely — they never completed registration.
     */
    private void processInactiveUser(UserSocialConnection connection, User user) {
        log.info("GDPR: Deauthorization Branch A (inactive user): userId={}, no verified email — archiving", user.getId());

        connection.setConnectionStatus(ConnectionStatus.DISCONNECTED);
        socialConnectionRepository.save(connection);

        // Archive user (sets TO_BE_DELETED, anonymizes PII)
        try {
            userAccountOrchestrator.archiveUser(user);
        } catch (Exception e) {
            log.error("GDPR: Failed to archive inactive user {} during deauthorization: {}", user.getId(), e.getMessage());
        }

        // Delete Firestore token data (non-fatal)
        try {
            firestoreService.deleteInstagramUserData(user.getFirebaseUserId());
        } catch (Exception e) {
            log.error("GDPR: Failed to delete Firestore data for user {}: {}", user.getFirebaseUserId(), e.getMessage());
        }

        log.info("GDPR: Inactive user {} archived after Instagram deauthorization", user.getId());
    }

    private String getUserLanguage(User user) {
        UserPreferences preferences = userPreferencesRepository.findByUserId(user.getId());
        if (preferences != null && preferences.getLanguage() != null) {
            return preferences.getLanguage();
        }
        return "en";
    }
}
