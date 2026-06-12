package com.sm.instagram.platform.auth.stepup;

import com.sm.instagram.platform.auth.service.TwoFactorAuthService;
import com.sm.instagram.platform.auth.stepup.dto.StepUpCheckResponse;
import com.sm.instagram.platform.auth.stepup.dto.StepUpTokenResponse;
import com.sm.instagram.platform.common.exceptions.AuthenticationTranslatableException;
import com.sm.instagram.platform.common.exceptions.InsufficientPermissionsException;
import com.sm.instagram.platform.common.exceptions.RateLimitTranslatableException;
import com.sm.instagram.platform.common.exceptions.ResourceNotFoundException;
import com.sm.instagram.platform.common.exceptions.ValidationTranslatableException;
import com.sm.instagram.platform.common.utils.HashingUtil;
import com.sm.instagram.platform.support.common.EmailService;
import com.sm.instagram.platform.user.User;
import com.sm.instagram.platform.user.UserRepository;
import com.sm.instagram.platform.user.UserType;
import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class StepUpAuthService {

    private final StringRedisTemplate redisTemplate;
    private final EmailService emailService;
    private final TwoFactorAuthService twoFactorAuthService;
    private final UserRepository userRepository;

    private static final int MAX_ATTEMPTS_PER_CODE = 5;
    private static final int MAX_CYCLES_BEFORE_LOCKOUT = 3;
    private static final Duration CODE_TTL = Duration.ofMinutes(10);
    private static final Duration COOLDOWN_TTL = Duration.ofMinutes(15);
    private static final Duration LOCKOUT_TTL = Duration.ofHours(24);
    private static final Duration CYCLE_TTL = Duration.ofHours(24);
    private static final Duration TOKEN_TTL = Duration.ofMinutes(10);

    private static final String CODE_PREFIX = "step_up_code";
    private static final String ATTEMPTS_PREFIX = "step_up_attempts";
    private static final String COOLDOWN_PREFIX = "step_up_cooldown";
    private static final String CYCLES_PREFIX = "step_up_cycles";
    private static final String LOCKOUT_PREFIX = "step_up_lockout";
    private static final String TOKEN_PREFIX = "step_up_token";

    private final SecureRandom secureRandom = new SecureRandom();

    public StepUpCheckResponse checkRequirement(String firebaseUid, StepUpActionType action) {
        User user = userRepository.findByFirebaseUserId(firebaseUid)
                .orElseThrow(() -> new ResourceNotFoundException("error.business.item_not_found", "User"));

        UserType userType = user.getUserType();

        if (userType == UserType.PENDING_ADMIN) {
            throw new InsufficientPermissionsException("error.stepup.email_change_blocked");
        }

        if (userType == UserType.ADMIN) {
            return StepUpCheckResponse.builder()
                    .required(true)
                    .challengeType(StepUpChallengeType.TOTP)
                    .build();
        }

        // COMPANY / INFLUENCER — require completed initial account setup
        if (!Boolean.TRUE.equals(user.getInitialAccountSetupCompleted())) {
            log.info("GDPR: Operation=stepUpSkipped, FirebaseUID={}, Action={}, Purpose=initial_setup_incomplete",
                    firebaseUid, action);
            return StepUpCheckResponse.builder()
                    .required(false)
                    .build();
        }

        return StepUpCheckResponse.builder()
                .required(true)
                .challengeType(StepUpChallengeType.EMAIL_CODE)
                .build();
    }

    public void requestCode(String firebaseUid, StepUpActionType action, String language) {
        checkLockout(firebaseUid, action);
        checkCooldown(firebaseUid, action);

        User user = userRepository.findByFirebaseUserId(firebaseUid)
                .orElseThrow(() -> new ResourceNotFoundException("error.business.item_not_found", "User"));

        // Generate 6-digit code
        String code = String.format("%06d", secureRandom.nextInt(1_000_000));
        String codeHash = DigestUtils.sha256Hex(code);

        // Store hashed code in Redis
        String codeKey = buildKey(CODE_PREFIX, firebaseUid, action);
        redisTemplate.opsForValue().set(codeKey, codeHash, CODE_TTL);

        // Reset attempts counter for new code
        String attemptsKey = buildKey(ATTEMPTS_PREFIX, firebaseUid, action);
        redisTemplate.delete(attemptsKey);

        // Send code to user's LAST VERIFIED email (security: never to the new unverified email)
        try {
            emailService.sendStepUpCodeEmail(user.getLastVerifiedEmail(), user.getFirstName(), code, language);
        } catch (MessagingException e) {
            log.error("Failed to send step-up code email to user {}: {}", firebaseUid, e.getMessage());
            // Clean up the stored code since email failed
            redisTemplate.delete(codeKey);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to send verification email");
        }

        log.info("GDPR: Operation=stepUpCodeSent, FirebaseUID={}, Action={}, Purpose=step_up_verification",
                firebaseUid, action);
    }

    public StepUpTokenResponse verifyCode(String firebaseUid, StepUpActionType action, String code) {
        checkLockout(firebaseUid, action);

        String attemptsKey = buildKey(ATTEMPTS_PREFIX, firebaseUid, action);

        // Increment attempts
        Long attempts = redisTemplate.opsForValue().increment(attemptsKey);
        if (attempts != null && attempts == 1) {
            redisTemplate.expire(attemptsKey, CODE_TTL);
        }

        // Check if too many attempts for this code
        if (attempts != null && attempts >= MAX_ATTEMPTS_PER_CODE) {
            // Invalidate code and set cooldown
            redisTemplate.delete(buildKey(CODE_PREFIX, firebaseUid, action));
            redisTemplate.delete(attemptsKey);
            redisTemplate.opsForValue().set(buildKey(COOLDOWN_PREFIX, firebaseUid, action), "1", COOLDOWN_TTL);

            // Increment cycles
            String cyclesKey = buildKey(CYCLES_PREFIX, firebaseUid, action);
            Long cycles = redisTemplate.opsForValue().increment(cyclesKey);
            if (cycles != null && cycles == 1) {
                redisTemplate.expire(cyclesKey, CYCLE_TTL);
            }

            // Check for lockout
            if (cycles != null && cycles >= MAX_CYCLES_BEFORE_LOCKOUT) {
                redisTemplate.opsForValue().set(buildKey(LOCKOUT_PREFIX, firebaseUid, action), "1", LOCKOUT_TTL);
                log.warn("GDPR: Operation=stepUpLockout, FirebaseUID={}, Action={}, Purpose=brute_force_protection",
                        firebaseUid, action);
                throw new ResponseStatusException(HttpStatus.LOCKED, "Account temporarily locked");
            }

            throw new RateLimitTranslatableException("error.stepup.cooldown");
        }

        // Verify code
        String codeKey = buildKey(CODE_PREFIX, firebaseUid, action);
        String storedHash = redisTemplate.opsForValue().get(codeKey);

        if (storedHash == null) {
            log.warn("Step-up verify failed: no stored hash for key={}, user={}", codeKey, firebaseUid);
            throw new ValidationTranslatableException("error.stepup.invalid_code");
        }

        String inputHash = DigestUtils.sha256Hex(code);
        if (!storedHash.equals(inputHash)) {
            log.warn("Step-up verify failed: hash mismatch for user={}, codeLength={}", firebaseUid, code.length());
            throw new ValidationTranslatableException("error.stepup.invalid_code");
        }

        // Code is valid — clean up and generate token
        redisTemplate.delete(codeKey);
        redisTemplate.delete(attemptsKey);
        redisTemplate.delete(buildKey(COOLDOWN_PREFIX, firebaseUid, action));

        return generateAndStoreToken(firebaseUid, action);
    }

    public StepUpTokenResponse verifyAdminTotp(String firebaseUid, StepUpActionType action, String code) {
        boolean valid = twoFactorAuthService.verifyTotpCodeOnly(firebaseUid, code);

        if (!valid) {
            throw new ValidationTranslatableException("error.stepup.invalid_code");
        }

        return generateAndStoreToken(firebaseUid, action);
    }

    /**
     * Validate step-up token only if step-up is required for the given user/action.
     * Allows email changes without token when initial account setup is incomplete.
     */
    public void validateTokenIfRequired(String firebaseUid, StepUpActionType action, String token) {
        StepUpCheckResponse check = checkRequirement(firebaseUid, action);
        if (!check.isRequired()) {
            return;
        }
        validateAndConsumeToken(firebaseUid, action, token);
    }

    public void validateAndConsumeToken(String firebaseUid, StepUpActionType action, String token) {
        if (token == null || token.isBlank()) {
            throw new AuthenticationTranslatableException("error.stepup.invalid_token");
        }

        String tokenKey = buildKey(TOKEN_PREFIX, firebaseUid, action);
        // Atomic get-and-delete: prevents race condition where two concurrent requests
        // could both read the token before either deletes it
        String storedToken = redisTemplate.opsForValue().getAndDelete(tokenKey);

        if (storedToken == null || !storedToken.equals(token)) {
            throw new AuthenticationTranslatableException("error.stepup.invalid_token");
        }

        log.info("GDPR: Operation=stepUpTokenConsumed, FirebaseUID={}, Action={}, Purpose=step_up_verification_complete",
                firebaseUid, action);
    }

    private StepUpTokenResponse generateAndStoreToken(String firebaseUid, StepUpActionType action) {
        String token = UUID.randomUUID().toString();
        String tokenKey = buildKey(TOKEN_PREFIX, firebaseUid, action);
        redisTemplate.opsForValue().set(tokenKey, token, TOKEN_TTL);

        log.info("GDPR: Operation=stepUpTokenGenerated, FirebaseUID={}, Action={}, Purpose=step_up_verification",
                firebaseUid, action);

        return StepUpTokenResponse.builder()
                .success(true)
                .token(token)
                .expiresInSeconds((int) TOKEN_TTL.getSeconds())
                .build();
    }

    private void checkLockout(String firebaseUid, StepUpActionType action) {
        String lockoutKey = buildKey(LOCKOUT_PREFIX, firebaseUid, action);
        if (Boolean.TRUE.equals(redisTemplate.hasKey(lockoutKey))) {
            throw new ResponseStatusException(HttpStatus.LOCKED, "Account temporarily locked");
        }
    }

    private void checkCooldown(String firebaseUid, StepUpActionType action) {
        String cooldownKey = buildKey(COOLDOWN_PREFIX, firebaseUid, action);
        if (Boolean.TRUE.equals(redisTemplate.hasKey(cooldownKey))) {
            throw new RateLimitTranslatableException("error.stepup.cooldown");
        }
    }

    private String buildKey(String prefix, String firebaseUid, StepUpActionType action) {
        return HashingUtil.generateRedisKey(prefix, firebaseUid) + ":" + action.name();
    }
}
