package com.sm.instagram.platform.auth.service;

import java.time.ZoneId;
import java.time.Instant;
import com.google.firebase.auth.ActionCodeSettings;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import com.sm.instagram.platform.address.Address;
import com.sm.instagram.platform.common.authorization.PermissionUtils;
import com.sm.instagram.platform.common.exceptions.ExternalServiceException;
import com.sm.instagram.platform.common.exceptions.RateLimitTranslatableException;
import com.sm.instagram.platform.common.exceptions.ResourceNotFoundException;
import com.sm.instagram.platform.common.exceptions.ValidationTranslatableException;
import com.sm.instagram.platform.auth.cache.UserCacheService;
import com.sm.instagram.platform.notification.event.AccountActivatedEvent;
import com.sm.instagram.platform.registry.CompanyDataRepository;
import com.sm.instagram.platform.user.AccountStatus;
import com.sm.instagram.platform.user.UserType;
import jakarta.mail.MessagingException;
import com.sm.instagram.platform.support.common.EmailService;
import com.sm.instagram.platform.user.User;
import com.sm.instagram.platform.user.UserRepository;
import com.sm.instagram.platform.userpreferences.UserPreferences;
import com.sm.instagram.platform.userpreferences.UserPreferencesRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.time.LocalDateTime;
import com.sm.instagram.platform.common.util.LogSafe;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailVerificationService {

    private final FirebaseAuth firebaseAuth;
    private final UserRepository userRepository;
    private final UserPreferencesRepository userPreferencesRepository;
    private final EmailService emailService;
    private final PermissionUtils permissionUtils;
    private final CompanyDataRepository companyDataRepository;
    private final StringRedisTemplate redisTemplate;
    private final ApplicationEventPublisher eventPublisher;
    private final UserCacheService userCacheService;

    private static final String OOB_CODE_PREFIX = "email_verify:";
    private static final Duration OOB_CODE_TTL = Duration.ofHours(1);

    @Value("${frontend.url:https://localhost:4200}")
    private String frontendUrl;

    @Value("${auth.email-verification.cooldown-seconds:60}")
    private int verificationCooldownSeconds;

    /**
     * Send email verification to user.
     * Rate limited by @RateLimit at controller level.
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void sendVerificationEmail(String firebaseUid, String language) {
        User user = userRepository.findByFirebaseUserId(firebaseUid)
            .orElseThrow(() -> new ResourceNotFoundException("error.business.item_not_found", "User"));

        // Check if already verified in PostgreSQL (fast check)
        if (Boolean.TRUE.equals(user.getEmailVerified())) {
            throw new ValidationTranslatableException("error.auth.email_already_verified");
        }

        // Check email exists
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            throw new ValidationTranslatableException("error.validation.email_required");
        }

        // Check Firebase verification status (source of truth)
        // Handles edge case: user verified in Firebase but PostgreSQL out of sync post-migration
        try {
            var firebaseUser = firebaseAuth.getUser(firebaseUid);
            if (firebaseUser.isEmailVerified()) {
                // Sync PostgreSQL to match Firebase and reject
                user.setEmailVerified(true);
                user.setEmailVerifiedAt(LocalDateTime.now());
                user.setLastVerifiedEmail(user.getEmail());
                userRepository.save(user);
                log.info("Synced email verification status from Firebase for user: {}", firebaseUid);
                throw new ValidationTranslatableException("error.auth.email_already_verified");
            }
        } catch (ValidationTranslatableException e) {
            throw e;
        } catch (FirebaseAuthException e) {
            log.warn("Could not check Firebase verification status for user {}: {}", firebaseUid, e.getMessage());
            // Continue - we'll try to send anyway; worst case Firebase rejects
        }

        // Secondary rate limit check (belt-and-suspenders)
        if (!canSendVerificationEmail(user)) {
            throw new RateLimitTranslatableException("error.ratelimit.verification_email");
        }

        try {
            // Generate Firebase verification link
            String redirectUrl = frontendUrl + "/settings/account";
            ActionCodeSettings settings = ActionCodeSettings.builder()
                .setUrl(redirectUrl)
                .setHandleCodeInApp(false)
                .build();

            String firebaseLink = firebaseAuth.generateEmailVerificationLink(user.getEmail(), settings);
            String oobCode = extractOobCode(firebaseLink);
            String ut = switch (user.getUserType()) {
                case INFLUENCER -> "I";
                case COMPANY -> "C";
                case ADMIN, PENDING_ADMIN -> "A";
            };
            String iac = Boolean.TRUE.equals(user.getInitialAccountSetupCompleted()) ? "1" : "0";
            String verificationLink = frontendUrl + "/auth/action?mode=verifyEmail&oobCode=" + oobCode + "&ut=" + ut + "&iac=" + iac;

            // Store oobCode→user mapping in Redis for server-side verification
            storeOobCode(oobCode, user.getFirebaseUserId(), user.getEmail());

            // Determine language (fallback to user preferences or 'en')
            String userLanguage = language != null ? language : getUserLanguage(user);

            // Send email - NOW SYNCHRONOUS, throws if fails
            emailService.sendVerificationEmail(
                user.getEmail(),
                user.getFirstName() != null ? user.getFirstName() : "User",
                verificationLink,
                userLanguage
            );

            // Update timestamp ONLY after successful email send
            // MEDIUM-002 FIX: Handle concurrent user updates
            try {
                user.setEmailVerificationSentAt(LocalDateTime.now());
                userRepository.save(user);
            } catch (OptimisticLockingFailureException e) {
                log.warn("Concurrent user update, verification timestamp not saved: {}", e.getMessage());
                // Email already sent, timestamp update is non-critical
            }

            log.info("Verification email sent to user: {}", firebaseUid);

        } catch (FirebaseAuthException e) {
            String errorCode = e.getErrorCode() != null ? e.getErrorCode().toString() : "";
            String errorMessage = e.getMessage() != null ? e.getMessage() : "";

            // Check for rate limiting from Firebase (TOO_MANY_ATTEMPTS_TRY_LATER)
            if (errorCode.contains("TOO_MANY") || errorMessage.contains("TOO_MANY_ATTEMPTS")) {
                log.warn("Firebase rate limit hit for verification email: {}", errorMessage);
                throw new RateLimitTranslatableException("error.ratelimit.verification_email");
            }

            log.error("Firebase failed to generate verification link [errorCode={}]: {}", errorCode, errorMessage);
            throw new ExternalServiceException("Failed to generate verification link", "Firebase",
                "generateEmailVerificationLink", e);
        } catch (MessagingException e) {
            log.error("SMTP failed to send verification email: {}", e.getMessage());
            throw new ExternalServiceException("Failed to send verification email", "SMTP",
                "sendEmail", e);
        }
    }

    /**
     * Sync Firebase emailVerified status to PostgreSQL.
     * Called during login (TokenExchangeService).
     *
     * <p>BUG-14: the {@code userInput} reference comes from the caller's outer
     * @Transactional(readOnly=true) context, so inside this REQUIRES_NEW the reference
     * is detached. Mutating it + calling {@code save()} on it triggered intermittent
     * OptimisticLockingFailureExceptions and version-skew between caller and DB.
     * Fix: refetch a managed reference inside this REQUIRES_NEW persistence context,
     * apply all changes to that managed copy, save, then mirror the auth-relevant
     * fields back to the caller's reference so downstream session-minting code sees
     * the freshly-synced state. The caller's outer tx is readOnly so the back-copy
     * does not flush.
     *
     * @param userInput User entity (already fetched by caller; used only for id +
     *                  field mirror-back at the end)
     * @param firebaseEmailVerified Status from Firebase token
     * @return true if status was updated
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public boolean syncEmailVerificationStatus(User userInput, boolean firebaseEmailVerified) {
        return syncEmailVerificationStatusInternal(userInput, firebaseEmailVerified);
    }

    /**
     * The body of {@link #syncEmailVerificationStatus}, without the transaction attribute.
     *
     * <p>applyVerificationCode below calls this one. It used to call the public method, and a
     * self-invocation never reaches the proxy -- so REQUIRES_NEW was silently dropped there and
     * the sync ran in whatever transaction that caller had, which is none (sonar java:S2229).
     * Splitting it keeps exactly that behaviour and stops the annotation promising the other one
     * to a reader. The three callers outside this class still go through the proxy and still get
     * their own transaction, which is what they were written for.
     *
     * <p>Whether the verification flow <em>should</em> commit the sync separately is a question
     * about the flow, not about the annotation, and it is the owner's to answer.
     */
    private boolean syncEmailVerificationStatusInternal(User userInput, boolean firebaseEmailVerified) {
        // BUG-14: refetch a managed reference in THIS persistence context.
        User user = userRepository.findById(userInput.getId())
                .orElseThrow(() -> new ResourceNotFoundException("error.business.item_not_found", "User"));

        // PG-wins enforcement: if initial setup not completed but Firebase says verified, reset Firebase
        // Admins are pre-provisioned and exempt from the onboarding flow
        if (!Boolean.TRUE.equals(user.getInitialAccountSetupCompleted())
                && firebaseEmailVerified
                && user.getUserType() != UserType.ADMIN
                && user.getUserType() != UserType.PENDING_ADMIN) {
            try {
                firebaseAuth.updateUser(new UserRecord.UpdateRequest(user.getFirebaseUserId()).setEmailVerified(false));
                log.info("PG-wins enforcement: reset Firebase email_verified=false for user {} (initialAccountSetupCompleted=false)",
                        user.getFirebaseUserId());
            } catch (FirebaseAuthException e) {
                log.warn("Failed to reset Firebase email_verified for user {}: {}", user.getFirebaseUserId(), e.getMessage());
            }
            firebaseEmailVerified = false;
        }

        // Validate user has email if setting verified=true
        if (firebaseEmailVerified && (user.getEmail() == null || user.getEmail().isBlank())) {
            log.warn("Attempted to set emailVerified=true for user {} with null/blank email", user.getFirebaseUserId());
            return false;
        }

        // Only update if status changed
        if (firebaseEmailVerified != Boolean.TRUE.equals(user.getEmailVerified())) {
            user.setEmailVerified(firebaseEmailVerified);
            if (firebaseEmailVerified) {
                user.setLastVerifiedEmail(user.getEmail());
            }
            // Auto-activation now also requires a complete profile for INFLUENCERs. Centralized in
            // evaluateAutoActivation; the Firebase role write + activation event are DEFERRED into this
            // list and run only after the PG save succeeds (no Firebase/PG split-brain on save failure).
            java.util.List<Runnable> deferredFirebaseActions = new java.util.ArrayList<>();
            evaluateAutoActivation(user, deferredFirebaseActions);
            if (firebaseEmailVerified && user.getEmailVerifiedAt() == null) {
                user.setEmailVerifiedAt(LocalDateTime.now());
            }

            // Activation trigger: email verified + profile complete → mark initial setup as done
            if (firebaseEmailVerified && !Boolean.TRUE.equals(user.getInitialAccountSetupCompleted())) {
                if (isProfileComplete(user)) {
                    user.setInitialAccountSetupCompleted(true);
                    log.info("Initial account setup completed for user: {} (email verified + profile complete)", user.getFirebaseUserId());
                }
            }

            userRepository.save(user);
            // Save succeeded — now run the deferred Firebase role write + activation event.
            deferredFirebaseActions.forEach(Runnable::run);
            // emailVerified is an authorization-relevant datum: EmailVerificationEnforcementFilter reads it
            // FROM cache to gate POST campaign/application. Evict on EVERY change — not only when the change
            // also auto-activated. A true->false flip used to skip eviction (evaluateAutoActivation returns
            // false when setting emailVerified=false), leaving a stale 'true' in cache that let an unverified
            // user POST campaigns/applications until the 5-min TTL expired.
            userCacheService.evict(user.getFirebaseUserId());
            log.info("Email verification status synced for user: {} -> {}", user.getFirebaseUserId(), firebaseEmailVerified);

            // BUG-14: mirror auth-relevant fields back to the caller's reference so downstream
            // session-minting (claims build in TokenExchangeService) sees the post-sync state.
            // The outer tx is readOnly — these mutations stay in-memory and do not flush.
            if (user != userInput) {
                userInput.setEmailVerified(user.getEmailVerified());
                userInput.setEmailVerifiedAt(user.getEmailVerifiedAt());
                userInput.setLastVerifiedEmail(user.getLastVerifiedEmail());
                userInput.setAccountStatus(user.getAccountStatus());
                userInput.setTokenVersion(user.getTokenVersion());
                userInput.setInitialAccountSetupCompleted(user.getInitialAccountSetupCompleted());
            }
            return true;
        }
        return false;
    }

    /**
     * Centralized, idempotent auto-activation evaluator. Reachable from BOTH the
     * email-verification path and the profile-update path (UserService). Activates
     * ONLY from IN_VALIDATION (never reactivates BANNED/BLOCKED/INACTIVE). Does NOT
     * save — the caller persists. Returns true iff status was flipped to ACTIVE.
     */
    public boolean evaluateAutoActivation(User user) {
        // Convenience overload for direct/test callers without a save boundary: run the deferred
        // Firebase actions immediately. Production callers (UserService.patch, syncEmailVerificationStatus)
        // MUST use the two-arg form and run the deferred list only after their PG save succeeds.
        java.util.List<Runnable> immediate = new java.util.ArrayList<>();
        boolean activated = evaluateAutoActivation(user, immediate);
        immediate.forEach(Runnable::run);
        return activated;
    }

    public boolean evaluateAutoActivation(User user, java.util.List<Runnable> deferredFirebaseActions) {
        if (user == null
                || user.getAccountStatus() != AccountStatus.IN_VALIDATION
                || !Boolean.TRUE.equals(user.getEmailVerified())) {
            return false;
        }
        if (user.getUserType() == UserType.INFLUENCER) {
            if (!isProfileComplete(user)) {
                return false;
            }
            queueActivation(user, UserType.INFLUENCER, deferredFirebaseActions);
            log.info("INFLUENCER user {} auto-activated: email verified + profile complete", user.getFirebaseUserId());
            return true;
        }
        if (user.getUserType() == UserType.COMPANY
                && companyDataRepository.existsByUserIdAndDataVerifiedTrue(user.getId())) {
            queueActivation(user, UserType.COMPANY, deferredFirebaseActions);
            log.info("COMPANY user {} auto-activated: email verified + company data verified", user.getFirebaseUserId());
            return true;
        }
        return false;
    }

    /**
     * Flip the entity to ACTIVE + bump tokenVersion (transactional — rolls back with the tx), and DEFER
     * the external Firebase role write + activation event into {@code deferredFirebaseActions} so the
     * caller runs them only AFTER a successful PG save. Doing the Firebase write inline (before save)
     * risked leaving Firebase ACTIVE while a failed/rolled-back save kept PG at IN_VALIDATION — a
     * split-brain with no tokenVersion bump (so no 419) that cannot self-heal.
     */
    private void queueActivation(User user, UserType type, java.util.List<Runnable> deferredFirebaseActions) {
        user.setAccountStatus(AccountStatus.ACTIVE);
        user.incrementTokenVersion();
        String firebaseUid = user.getFirebaseUserId();
        deferredFirebaseActions.add(() -> permissionUtils.changeUserRole(firebaseUid, AccountStatus.ACTIVE, type));
        deferredFirebaseActions.add(() -> eventPublisher.publishEvent(
                new AccountActivatedEvent(this, user, AccountStatus.IN_VALIDATION, "EMAIL_VERIFICATION")));
    }

    private boolean canSendVerificationEmail(User user) {
        if (user.getEmailVerificationSentAt() == null) {
            return true;
        }
        // Both ends through the same zone before subtracting. Duration.between on two
        // LocalDateTime values counts wall-clock, so a daylight-saving transition between them
        // makes it an hour out (java:S8700) -- and this is a security cooldown, so an hour out is
        // either a lockout or a free retry.
        ZoneId zone = ZoneId.systemDefault();
        Duration timeSinceLastSent = Duration.between(
            user.getEmailVerificationSentAt().atZone(zone).toInstant(),
            Instant.now()
        );
        return timeSinceLastSent.toSeconds() >= verificationCooldownSeconds;
    }

    private String getUserLanguage(User user) {
        UserPreferences preferences = userPreferencesRepository.findByUserId(user.getId());
        if (preferences != null && preferences.getLanguage() != null) {
            return preferences.getLanguage();
        }
        return "en";
    }

    private String extractOobCode(String firebaseLink) {
        String oobCode = UriComponentsBuilder.fromUriString(firebaseLink)
                .build().getQueryParams().getFirst("oobCode");
        if (oobCode == null || oobCode.isBlank()) {
            log.error("Failed to extract oobCode from Firebase link");
            throw new ExternalServiceException("Failed to extract verification code", "Firebase",
                    "generateEmailVerificationLink", null);
        }
        return oobCode;
    }

    // ========================================================================
    // OOB CODE STORAGE — Redis-backed for server-side email verification
    // ========================================================================

    /**
     * Store oobCode → {firebaseUid, email} mapping in Redis.
     * Called when generating a verification link. Public so test endpoints can use it.
     */
    // javasecurity:S5145. The value IS sanitised -- LogSafe.value is this codebase's one
    // implementation of the control, and the line below calls it. The taint engine does not
    // recognise it as a sanitiser: it is a regex replace of a character class in another file,
    // not one of the shapes the rule knows. Inlining a second copy of that regex here would
    // satisfy the tool and give the codebase two implementations of one security control,
    // which is the mistake LogSafe exists to have already fixed.
    @SuppressWarnings("javasecurity:S5145")
    public void storeOobCode(String oobCode, String firebaseUid, String email) {
        String key = OOB_CODE_PREFIX + oobCode;
        String value = firebaseUid + "|" + email;
        redisTemplate.opsForValue().set(key, value, OOB_CODE_TTL);
        log.debug("Stored verification oobCode mapping for user: {}", LogSafe.value(firebaseUid));
    }

    /**
     * Look up oobCode data from Redis.
     *
     * @return [firebaseUid, email] or null if invalid/expired
     */
    public String[] lookupOobCode(String oobCode) {
        String key = OOB_CODE_PREFIX + oobCode;
        String value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            return null;
        }
        String[] parts = value.split("\\|", 2);
        return parts.length == 2 ? parts : null;
    }

    /**
     * Invalidate an oobCode after successful verification (one-time use).
     */
    public void invalidateOobCode(String oobCode) {
        redisTemplate.delete(OOB_CODE_PREFIX + oobCode);
    }

    /**
     * Apply email verification using a stored oobCode.
     * Looks up the oobCode in Redis, sets emailVerified=true via Admin SDK,
     * syncs to PostgreSQL, and invalidates the oobCode.
     *
     * @param oobCode The verification code from the email link
     * @return the user's email address
     * @throws ValidationTranslatableException if oobCode is invalid/expired/already used
     */
    public String applyVerificationCode(String oobCode) {
        String[] data = lookupOobCode(oobCode);
        if (data == null) {
            log.warn("Invalid or expired verification oobCode attempted");
            throw new ValidationTranslatableException("error.auth.invalid_action_code");
        }

        String firebaseUid = data[0];
        String email = data[1];

        try {
            // Set emailVerified=true via Admin SDK (privileged, no idToken needed)
            UserRecord.UpdateRequest updateRequest = new UserRecord.UpdateRequest(firebaseUid);
            updateRequest.setEmailVerified(true);
            firebaseAuth.updateUser(updateRequest);

            // Sync to PostgreSQL
            userRepository.findByFirebaseUserId(firebaseUid).ifPresent(user ->
                    syncEmailVerificationStatusInternal(user, true)
            );

            // Invalidate (one-time use)
            invalidateOobCode(oobCode);

            log.info("Email verification applied via Admin SDK for user={}", firebaseUid);
            return email;

        } catch (FirebaseAuthException e) {
            String errorCode = e.getErrorCode() != null ? e.getErrorCode().toString() : "";
            log.error("Admin SDK failed to verify email for uid={}: [{}] {}", firebaseUid, errorCode, e.getMessage());

            if (errorCode.contains("USER_NOT_FOUND")) {
                throw new ValidationTranslatableException("error.auth.invalid_action_code");
            }
            throw new ExternalServiceException("Failed to verify email", "Firebase", "updateUser", e);
        }
    }

    /**
     * Check if user profile has all required fields filled.
     * Mirrors UserService.checkProfileCompleteness() but avoids circular dependency.
     */
    static boolean isProfileComplete(User user) {
        if (user.getFirstName() == null || user.getFirstName().isBlank()) return false;
        if (user.getLastName() == null || user.getLastName().isBlank()) return false;
        if (user.getEmail() == null || user.getEmail().isBlank()) return false;
        if (user.getPhoneNumber() == null || user.getPhoneNumber().isBlank()) return false;

        Address primaryAddress = user.getAddresses() == null ? null :
                user.getAddresses().stream().filter(Address::isPrimary).findFirst().orElse(null);
        if (primaryAddress == null) return false;
        if (primaryAddress.getStreet() == null || primaryAddress.getStreet().isBlank()) return false;
        if (primaryAddress.getCity() == null || primaryAddress.getCity().isBlank()) return false;
        if (primaryAddress.getPostalCode() == null || primaryAddress.getPostalCode().isBlank()) return false;
        if (primaryAddress.getCountry() == null || primaryAddress.getCountry().isBlank()) return false;
        if (primaryAddress.getState() == null || primaryAddress.getState().isBlank()) return false;

        return true;
    }
}
