package com.sm.instagram.platform.auth.stepup;

import com.sm.instagram.platform.auth.stepup.dto.StepUpCheckResponse;
import com.sm.instagram.platform.auth.stepup.dto.StepUpRequestDto;
import com.sm.instagram.platform.auth.stepup.dto.StepUpTokenResponse;
import com.sm.instagram.platform.auth.stepup.dto.StepUpVerifyDto;
import com.sm.instagram.platform.common.authorization.PermissionUtils;
import com.sm.instagram.platform.common.ratelimit.RateLimit;
import com.sm.instagram.platform.common.ratelimit.RateLimitProfile;
import com.sm.instagram.platform.user.User;
import com.sm.instagram.platform.user.UserRepository;
import com.sm.instagram.platform.user.UserType;
import com.sm.instagram.platform.common.exceptions.ResourceNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/step-up")
@PreAuthorize("isAuthenticated()")
@RateLimit(profile = RateLimitProfile.STRICT)
@RequiredArgsConstructor
public class StepUpAuthController {

    private final StepUpAuthService stepUpAuthService;
    private final PermissionUtils permissionUtils;
    private final UserRepository userRepository;

    @GetMapping("/check")
    public ResponseEntity<StepUpCheckResponse> checkRequirement(
            @RequestParam StepUpActionType actionType) {
        String firebaseUid = permissionUtils.getUserId();
        StepUpCheckResponse response = stepUpAuthService.checkRequirement(firebaseUid, actionType);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/request")
    public ResponseEntity<Map<String, Object>> requestCode(
            @RequestBody @Valid StepUpRequestDto dto) {
        String firebaseUid = permissionUtils.getUserId();
        String language = LocaleContextHolder.getLocale().getLanguage();

        // Check what challenge type this user needs
        StepUpCheckResponse check = stepUpAuthService.checkRequirement(firebaseUid, dto.getActionType());

        // If step-up not required (e.g., initial setup incomplete), no code to send
        if (!check.isRequired()) {
            return ResponseEntity.ok(Map.of("success", true, "required", false));
        }

        if (check.getChallengeType() == StepUpChallengeType.TOTP) {
            // ADMIN users use TOTP — no email code to send
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "challengeType", StepUpChallengeType.TOTP.name()
            ));
        }

        // EMAIL_CODE — send the code
        stepUpAuthService.requestCode(firebaseUid, dto.getActionType(), language);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "challengeType", StepUpChallengeType.EMAIL_CODE.name()
        ));
    }

    @PostMapping("/verify")
    public ResponseEntity<StepUpTokenResponse> verify(
            @RequestBody @Valid StepUpVerifyDto dto) {
        String firebaseUid = permissionUtils.getUserId();

        // Determine challenge type from user role
        User user = userRepository.findByFirebaseUserId(firebaseUid)
                .orElseThrow(() -> new ResourceNotFoundException("error.business.item_not_found", "User"));

        StepUpTokenResponse response;
        if (user.getUserType() == UserType.ADMIN) {
            response = stepUpAuthService.verifyAdminTotp(firebaseUid, dto.getActionType(), dto.getCode());
        } else {
            response = stepUpAuthService.verifyCode(firebaseUid, dto.getActionType(), dto.getCode());
        }

        return ResponseEntity.ok(response);
    }
}
