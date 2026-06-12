package com.sm.instagram.platform.auth.service;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import com.sm.instagram.platform.auth.cache.UserCacheService;
import com.sm.instagram.platform.auth.firebase.FirebaseService;
import com.sm.instagram.platform.common.exceptions.BusinessRuleTranslatableException;
import com.sm.instagram.platform.common.exceptions.ExternalServiceException;
import com.sm.instagram.platform.common.exceptions.ValidationTranslatableException;
import com.sm.instagram.platform.user.AccountStatus;
import com.sm.instagram.platform.user.User;
import com.sm.instagram.platform.user.UserRepository;
import com.sm.instagram.platform.user.UserType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Locale;
import java.util.Objects;

/**
 * Unified email change logic shared by all three code paths:
 * proxy (FirebaseAuthProxyService), PUT (UserService.update), PATCH (UserService.patch).
 *
 * No @Transactional — joins the caller's transaction (REQUIRED default).
 * The saveAndFlush ensures the unique constraint fires within the caller's transaction boundary.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailChangeService {

    private final UserRepository userRepository;
    private final FirebaseAuth firebaseAuth;
    private final FirebaseService firebaseService;
    private final UserCacheService userCacheService;
    private final EmailVerificationService emailVerificationService;

    /**
     * Unified email change: validate, PG first, Firebase second, verify email.
     *
     * @param user     Attached entity (caller's transaction)
     * @param newEmail Target email
     * @return true if email was changed, false if already same
     */
    public boolean changeEmail(User user, String newEmail) {
        // 1. Validate
        if (newEmail == null || newEmail.trim().isEmpty()) {
            throw new ValidationTranslatableException("error.validation.required_field", "email");
        }

        // 2. Normalize email to lowercase
        newEmail = newEmail.trim().toLowerCase(Locale.ROOT);

        // 3. Self-email guard
        String oldEmail = user.getEmail();
        if (Objects.equals(newEmail, oldEmail)) {
            return false;
        }

        // 4. PG duplicate (self-exclusion)
        userRepository.findByEmail(newEmail)
            .filter(u -> !u.getId().equals(user.getId()))
            .ifPresent(u -> {
                throw new BusinessRuleTranslatableException("error.auth.email_already_exists");
            });

        // 5. Firebase duplicate
        try {
            UserRecord existing = firebaseAuth.getUserByEmail(newEmail);
            if (existing != null && !existing.getUid().equals(user.getFirebaseUserId())) {
                throw new BusinessRuleTranslatableException("error.auth.email_already_exists");
            }
        } catch (BusinessRuleTranslatableException e) {
            throw e;
        } catch (FirebaseAuthException e) {
            String code = e.getErrorCode() != null ? e.getErrorCode().toString() : "UNKNOWN";
            if (!"NOT_FOUND".equals(code) && !"USER_NOT_FOUND".equals(code)) {
                throw new ExternalServiceException(
                    "Failed to validate email availability",
                    "Firebase", "getUserByEmail", e
                );
            }
        }

        // 6. PG update
        user.setEmail(newEmail);
        user.setEmailVerified(false);
        user.setEmailVerifiedAt(null);
        user.setEmailVerificationSentAt(null);

        // 7. Token version increment (forces session refresh to pick up emailVerified=false)
        // Note: Account status is NOT changed — user stays ACTIVE with emailVerified=false.
        // The EmailVerificationEnforcementFilter blocks sensitive actions until email is re-verified.
        user.incrementTokenVersion();

        // 8. Flush -> unique constraint fires NOW
        userRepository.saveAndFlush(user);

        // 9. Firebase REST API (irreversible, only after PG succeeded)
        if (user.getFirebaseUserId() != null) {
            firebaseService.updateFirebaseUserEmail(user.getFirebaseUserId(), newEmail, oldEmail);
        }

        // 10. Cache evict
        if (user.getFirebaseUserId() != null) {
            userCacheService.evict(user.getFirebaseUserId());
        }

        // 11. Verification email (best-effort, deferred to afterCommit to release DB connection)
        if (user.getFirebaseUserId() != null) {
            final String firebaseUid = user.getFirebaseUserId();
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try {
                            emailVerificationService.sendVerificationEmail(firebaseUid, null);
                        } catch (Exception e) {
                            log.warn("Failed to send verification email after email change: {}", e.getMessage());
                        }
                    }
                });
            } else {
                try {
                    emailVerificationService.sendVerificationEmail(firebaseUid, null);
                } catch (Exception e) {
                    log.warn("Failed to send verification email after email change: {}", e.getMessage());
                }
            }
        }

        log.info("Email changed for user {}: {} -> {}", user.getId(),
                 maskEmail(oldEmail), maskEmail(newEmail));
        return true;
    }

    private String maskEmail(String email) {
        if (email == null || email.length() < 4 || !email.contains("@")) {
            return "***";
        }
        String[] parts = email.split("@");
        String local = parts[0].length() > 3 ?
                parts[0].substring(0, 3) + "***" : "***";
        return local + "@" + parts[1];
    }
}
