package com.sm.instagram.platform.auth.service;

import com.google.firebase.auth.ActionCodeSettings;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.sm.instagram.platform.auth.dto.ForgotPasswordResponse;
import com.sm.instagram.platform.common.exceptions.ExternalServiceException;
import com.sm.instagram.platform.common.exceptions.RateLimitTranslatableException;
import com.sm.instagram.platform.support.common.EmailService;
import com.sm.instagram.platform.user.User;
import com.sm.instagram.platform.user.UserRepository;
import com.sm.instagram.platform.userpreferences.UserPreferences;
import com.sm.instagram.platform.userpreferences.UserPreferencesRepository;
import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Service for handling password reset requests.
 * Implements email verification requirement before allowing password reset.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordResetService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final FirebaseAuth firebaseAuth;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final EmailVerificationService emailVerificationService;
    private final UserPreferencesRepository userPreferencesRepository;
    private final PlatformTransactionManager transactionManager;

    @Value("${frontend.url:https://localhost:4200}")
    private String frontendUrl;

    @Value("${auth.password-reset.cooldown-seconds:60}")
    private int passwordResetCooldownSeconds;

    /** Result of the transactional DB phase — tells the non-transactional phase what to do. */
    private enum ResetAction { NONE, SEND_VERIFICATION, SEND_RESET }

    private record ResetContext(ResetAction action, String firebaseUid, String userLanguage, String firstName) {
        static ResetContext none() { return new ResetContext(ResetAction.NONE, null, null, null); }
    }

    /**
     * Request password reset. Returns response indicating what action was taken.
     * SECURITY: Response is generic to prevent email enumeration.
     *
     * DB operations run in a short-lived transaction (TransactionTemplate).
     * SMTP and timing normalization run OUTSIDE the transaction to avoid
     * holding a DB connection during network I/O or Thread.sleep.
     *
     * @param email    The email address to send reset link to
     * @param language The user's preferred language for email content
     * @return ForgotPasswordResponse indicating what action was taken
     */
    public ForgotPasswordResponse requestPasswordReset(String email, String language) {
        long startTime = System.currentTimeMillis();

        try {
            // Phase 1: Transactional — DB reads/writes only (no SMTP, no sleep)
            TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
            txTemplate.setTimeout(10);

            ResetContext ctx = txTemplate.execute(status -> executeTransactionalPhase(email, language));
            if (ctx == null) {
                ctx = ResetContext.none();
            }

            // Phase 2: Non-transactional — SMTP (network I/O, no DB connection held)
            if (ctx.action() == ResetAction.SEND_VERIFICATION && ctx.firebaseUid() != null) {
                try {
                    emailVerificationService.sendVerificationEmail(ctx.firebaseUid(), ctx.userLanguage());
                } catch (Exception e) {
                    log.warn("Failed to send verification email for user: {}", e.getMessage());
                }
            } else if (ctx.action() == ResetAction.SEND_RESET) {
                sendPasswordResetEmail(email, ctx.firstName(), ctx.userLanguage());
            }

            return ForgotPasswordResponse.success();

        } finally {
            // Phase 3: Timing normalization — Thread.sleep outside TX
            normalizeResponseTiming(startTime);
        }
    }

    /**
     * Transactional phase: user lookup, cooldown check, Firebase verification check, timestamp save.
     * No SMTP, no Thread.sleep — keeps the transaction short-lived.
     */
    private ResetContext executeTransactionalPhase(String email, String language) {
        // 1. Find user (silent fail if not found — anti-enumeration)
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            log.info("Password reset requested for non-existent email: {}", maskEmail(email));
            return ResetContext.none();
        }

        User user = userOpt.get();
        String userLanguage = language != null ? language : getUserLanguage(user);

        // 2. Cooldown check (60 seconds)
        if (user.getPasswordResetSentAt() != null) {
            Duration timeSinceLastRequest = Duration.between(
                user.getPasswordResetSentAt(),
                LocalDateTime.now()
            );
            if (timeSinceLastRequest.getSeconds() < passwordResetCooldownSeconds) {
                log.info("Password reset cooldown active for user: {} ({}s remaining)",
                    user.getId(), passwordResetCooldownSeconds - timeSinceLastRequest.getSeconds());
                return ResetContext.none();
            }
        }

        // 3. Check email verification in Firebase (source of truth)
        boolean isEmailVerifiedInFirebase = false;
        try {
            if (user.getFirebaseUserId() != null) {
                var firebaseUser = firebaseAuth.getUser(user.getFirebaseUserId());
                isEmailVerifiedInFirebase = firebaseUser.isEmailVerified();
            }
        } catch (FirebaseAuthException e) {
            log.warn("Could not check Firebase verification status for user {}: {}", user.getId(), e.getMessage());
            isEmailVerifiedInFirebase = Boolean.TRUE.equals(user.getEmailVerified());
        }

        // 4. Save timestamp BEFORE email send (cooldown enforcement + TOCTOU fix)
        try {
            user.setPasswordResetSentAt(LocalDateTime.now());
            userRepository.save(user);
        } catch (OptimisticLockingFailureException e) {
            log.warn("Concurrent user update during password reset for user: {}", user.getId());
            return ResetContext.none();
        } catch (Exception e) {
            log.error("Database error updating password reset timestamp: {}", e.getMessage());
            return ResetContext.none();
        }

        // 5. Return context for non-transactional phase
        if (!isEmailVerifiedInFirebase) {
            log.info("Password reset blocked - email not verified in Firebase for user: {}", user.getId());
            return new ResetContext(ResetAction.SEND_VERIFICATION, user.getFirebaseUserId(), userLanguage, user.getFirstName());
        }

        return new ResetContext(ResetAction.SEND_RESET, user.getFirebaseUserId(), userLanguage, user.getFirstName());
    }

    /**
     * Send password reset email (non-transactional, called after TX commits).
     */
    private void sendPasswordResetEmail(String email, String firstName, String language) {
        try {
            ActionCodeSettings settings = ActionCodeSettings.builder()
                    .setUrl(frontendUrl + "/auth/sign-in?passwordReset=success")
                    .setHandleCodeInApp(false)
                    .build();

            String firebaseLink = firebaseAuth.generatePasswordResetLink(email, settings);
            String oobCode = extractOobCode(firebaseLink);
            String resetLink = frontendUrl + "/auth/action?mode=resetPassword&oobCode=" + oobCode;

            emailService.sendPasswordResetEmail(
                    email,
                    firstName != null ? firstName : "User",
                    resetLink,
                    language
            );

            log.info("Password reset email sent successfully");

        } catch (FirebaseAuthException e) {
            String errorCode = e.getErrorCode() != null ? e.getErrorCode().toString() : "";
            String errorMessage = e.getMessage() != null ? e.getMessage() : "";

            if (errorCode.contains("TOO_MANY") || errorMessage.contains("TOO_MANY_ATTEMPTS")) {
                log.warn("Firebase internal rate limit hit (swallowed to prevent enumeration): {}", errorMessage);
            } else {
                log.error("Firebase password reset failed [errorCode={}]: {}", errorCode, errorMessage);
            }
        } catch (Exception e) {
            log.error("Failed to send password reset email: {}", e.getMessage(), e);
        }
    }

    /**
     * Get user's preferred language from preferences.
     */
    private String getUserLanguage(User user) {
        UserPreferences preferences = userPreferencesRepository.findByUserId(user.getId());
        if (preferences != null && preferences.getLanguage() != null) {
            return preferences.getLanguage();
        }
        return "en";
    }

    /**
     * Mask email for logging (privacy protection).
     * Sanitize newlines and control characters to prevent log injection.
     */
    private String maskEmail(String email) {
        if (email == null || email.isEmpty()) {
            return "N/A";
        }
        String sanitized = email.replaceAll("[\\n\\r\\t]", "_");
        return sanitized.replaceAll("(?<=.{3}).(?=.*@)", "*");
    }

    private String extractOobCode(String firebaseLink) {
        String oobCode = UriComponentsBuilder.fromUriString(firebaseLink)
                .build().getQueryParams().getFirst("oobCode");
        if (oobCode == null || oobCode.isBlank()) {
            log.error("Failed to extract oobCode from Firebase password reset link");
            throw new RuntimeException("Failed to extract password reset code from Firebase link");
        }
        return oobCode;
    }

    /**
     * Normalize response timing to prevent timing-based email enumeration.
     * All responses take approximately ~500ms ± random jitter.
     * Runs OUTSIDE the transaction — no DB connection held during sleep.
     */
    private void normalizeResponseTiming(long startTime) {
        long targetTime = 500;
        long elapsed = System.currentTimeMillis() - startTime;

        try {
            if (elapsed < targetTime) {
                long sleepTime = targetTime - elapsed + SECURE_RANDOM.nextInt(100);
                Thread.sleep(sleepTime);
            } else {
                Thread.sleep(SECURE_RANDOM.nextInt(50));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.debug("Timing normalization interrupted");
        }
    }
}
