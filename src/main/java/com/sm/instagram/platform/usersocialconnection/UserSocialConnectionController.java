package com.sm.instagram.platform.usersocialconnection;

import com.sm.instagram.platform.common.base.BaseController;
import com.sm.instagram.platform.common.base.BaseService;
import com.sm.instagram.platform.common.ratelimit.RateLimit;
import com.sm.instagram.platform.common.ratelimit.RateLimitKeyType;
import com.sm.instagram.platform.common.ratelimit.RateLimitProfile;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@PreAuthorize("isAuthenticated()")
@RequestMapping("user-social-connection")
@RateLimit(profile = RateLimitProfile.STANDARD, keyType = RateLimitKeyType.USER_ENDPOINT)
public class UserSocialConnectionController extends BaseController<UserSocialConnection, Long, UserSocialConnectionDtoIn, UserSocialConnectionDtoOut> {
    private final UserSocialConnectionService userSocialConnectionService;

    protected UserSocialConnectionController(UserSocialConnectionService userSocialConnectionService) {
        super(UserSocialConnection.class);
        this.userSocialConnectionService = userSocialConnectionService;
    }

    @Override
    protected BaseService<UserSocialConnection, Long, UserSocialConnectionDtoIn> getService() {
        return userSocialConnectionService;
    }

    @Override
    @PostMapping
    public ResponseEntity<UserSocialConnectionDtoOut> create(@Valid @RequestBody UserSocialConnectionDtoIn dto) {
        String firebaseUid = SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal().toString();

        // GDPR: Log social connection creation
        log.info("GDPR: Operation=createSocialConnection, FirebaseUID={}, Platform={}, Purpose=social_profile_linking, DataCreated=social.profile,social.username,social.followers, LegalBasis=consent",
                firebaseUid, dto.getPlatform());

        log.info("UserSocialConnectionController: Creating new UserSocialConnection");
        long startTime = System.currentTimeMillis();

        UserSocialConnectionDtoOut result = userSocialConnectionService.createAsDto(dto);

        long duration = System.currentTimeMillis() - startTime;
        log.info("UserSocialConnectionController: Successfully created UserSocialConnection in {}ms", duration);

        // GDPR: Log successful social connection
        log.info("GDPR: Operation=createSocialConnection_SUCCESS, FirebaseUID={}, ConnectionID={}, Platform={}, DisplayName={}, FollowersCount={}, DataStored=social_media_profile",
                firebaseUid, result.getId(), result.getPlatform() != null ? result.getPlatform().getName() : "unknown", result.getDisplayName(), result.getFollowersCount());

        return ResponseEntity.ok(result);
    }
}
