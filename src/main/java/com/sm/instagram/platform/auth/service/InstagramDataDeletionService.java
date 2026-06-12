package com.sm.instagram.platform.auth.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sm.instagram.platform.auth.dto.DataDeletionResponse;
import com.sm.instagram.platform.auth.dto.MetaCallbackPayload;
import com.sm.instagram.platform.auth.entity.DeletionRequestStatus;
import com.sm.instagram.platform.auth.entity.PendingDataDeletionRequest;
import com.sm.instagram.platform.auth.firebase.FirestoreService;
import com.sm.instagram.platform.auth.repository.PendingDataDeletionRequestRepository;
import com.sm.instagram.platform.user.AccountStatus;
import com.sm.instagram.platform.user.User;
import com.sm.instagram.platform.user.UserAccountOrchestrator;
import com.sm.instagram.platform.user.UserRepository;
import com.sm.instagram.platform.user.dto.DeletionEligibilityDto;
import com.sm.instagram.platform.usersocialconnection.ConnectionStatus;
import com.sm.instagram.platform.usersocialconnection.UserSocialConnection;
import com.sm.instagram.platform.usersocialconnection.UserSocialConnectionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

/**
 * Handles Instagram data deletion callbacks from Meta.
 * <p>
 * Case A: No active collaborations → immediate deletion via archiveUser()
 * Case B: Active collaborations → deferred deletion (queued in pending_data_deletion_request)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InstagramDataDeletionService {

    private final MetaSignedRequestService metaSignedRequestService;
    private final UserSocialConnectionRepository socialConnectionRepository;
    private final UserAccountOrchestrator userAccountOrchestrator;
    private final FirestoreService firestoreService;
    private final PendingDataDeletionRequestRepository deletionRequestRepository;
    private final UserRepository userRepository;
    private final com.sm.instagram.platform.auth.cache.UserCacheService userCacheService;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Value("${frontend.url:https://localhost:4200}")
    private String frontendUrl;

    /**
     * Process a Meta data deletion callback.
     *
     * @param signedRequest the raw signed_request from Meta
     * @return response with url and confirmation_code as required by Meta
     */
    @Transactional
    public DataDeletionResponse processDataDeletion(String signedRequest) {
        MetaCallbackPayload payload = metaSignedRequestService.parseSignedRequest(signedRequest);

        log.info("GDPR: Processing Instagram data deletion request for social user ID: {}", payload.userId());

        var connectionOpt = socialConnectionRepository.findByPlatform_NameAndSocialUserId(
                "Instagram", payload.userId());

        if (connectionOpt.isEmpty()) {
            log.warn("GDPR: Data deletion callback: no social connection found for Instagram user ID: {}", payload.userId());
            String confirmationCode = UUID.randomUUID().toString();
            return new DataDeletionResponse(
                    buildStatusUrl(confirmationCode),
                    confirmationCode
            );
        }

        UserSocialConnection connection = connectionOpt.get();
        User user = connection.getUser();

        // Idempotency: already processed (connection disconnected or user in deletion state)
        if (connection.getConnectionStatus() == ConnectionStatus.DISCONNECTED) {
            log.info("GDPR: Data deletion callback: connection already DISCONNECTED for user {}", user.getId());
            var existingRequest = deletionRequestRepository.findByUserId(user.getId());
            if (existingRequest.isPresent()) {
                return new DataDeletionResponse(
                        buildStatusUrl(existingRequest.get().getConfirmationCode()),
                        existingRequest.get().getConfirmationCode()
                );
            }
        }

        if (user.getAccountStatus() == AccountStatus.TO_BE_DELETED ||
                user.getAccountStatus() == AccountStatus.DELETED) {
            log.info("GDPR: Data deletion callback: user {} already in status {}", user.getId(), user.getAccountStatus());
            var existingRequest = deletionRequestRepository.findByUserId(user.getId());
            if (existingRequest.isPresent()) {
                return new DataDeletionResponse(
                        buildStatusUrl(existingRequest.get().getConfirmationCode()),
                        existingRequest.get().getConfirmationCode()
                );
            }
        }

        // Mark connection as DISCONNECTED
        connection.setConnectionStatus(ConnectionStatus.DISCONNECTED);
        socialConnectionRepository.save(connection);

        // Check deletion eligibility
        DeletionEligibilityDto eligibility = userAccountOrchestrator.checkDeletionEligibilityForUser(user, Locale.ENGLISH);

        String confirmationCode = UUID.randomUUID().toString();

        DataDeletionResponse response;
        if (eligibility.isCanSoftDelete()) {
            response = processImmediateDeletion(user, confirmationCode);
        } else {
            response = processDeferredDeletion(user, confirmationCode, eligibility);
        }

        // Delete Firestore data AFTER DB operations succeed.
        // Non-fatal: Meta already revoked the token, this is cleanup only.
        try {
            firestoreService.deleteInstagramUserData(user.getFirebaseUserId());
            log.info("GDPR: Firestore data deleted for user {}", user.getFirebaseUserId());
        } catch (Exception e) {
            log.error("GDPR: Failed to delete Firestore data for user {}: {}", user.getFirebaseUserId(), e.getMessage());
        }

        return response;
    }

    /**
     * Case A: No active collaborations — delete immediately.
     */
    private DataDeletionResponse processImmediateDeletion(User user, String confirmationCode) {
        log.info("GDPR: Data deletion Case A (immediate): userId={}, no active collaborations", user.getId());

        userAccountOrchestrator.archiveUser(user);

        PendingDataDeletionRequest request = PendingDataDeletionRequest.builder()
                .user(user)
                .confirmationCode(confirmationCode)
                .status(DeletionRequestStatus.COMPLETED)
                .requestedAt(LocalDateTime.now())
                .completedAt(LocalDateTime.now())
                .build();
        deletionRequestRepository.save(request);

        log.info("GDPR: User {} data deletion completed immediately, confirmation code: {}", user.getId(), confirmationCode);

        return new DataDeletionResponse(buildStatusUrl(confirmationCode), confirmationCode);
    }

    /**
     * Case B: Active collaborations — queue for deferred deletion.
     */
    private DataDeletionResponse processDeferredDeletion(User user, String confirmationCode,
                                                          DeletionEligibilityDto eligibility) {
        log.info("GDPR: Data deletion Case B (deferred): userId={}, has active collaborations", user.getId());

        user.setAccountStatus(AccountStatus.TO_BE_DELETED);
        userRepository.save(user);
        // TO_BE_DELETED is excluded from isUserActive (RedisUserCache), so this status blocks the
        // user -- but only once the cache reflects it. The immediate path (Case A) evicts via
        // archiveUser; the deferred path must evict too, or a stale "active" cache entry lets the
        // user keep acting for up to the 5-minute TTL after requesting deletion.
        userCacheService.evict(user.getFirebaseUserId());

        String blockersJson = serializeBlockers(eligibility);

        PendingDataDeletionRequest request = PendingDataDeletionRequest.builder()
                .user(user)
                .confirmationCode(confirmationCode)
                .status(DeletionRequestStatus.PENDING)
                .requestedAt(LocalDateTime.now())
                .blockers(blockersJson)
                .build();
        deletionRequestRepository.save(request);

        log.info("GDPR: User {} data deletion deferred, confirmation code: {}, blockers: {}",
                user.getId(), confirmationCode, blockersJson);

        return new DataDeletionResponse(buildStatusUrl(confirmationCode), confirmationCode);
    }

    private String buildStatusUrl(String confirmationCode) {
        return frontendUrl + "/deletion-status?code=" + confirmationCode;
    }

    private String serializeBlockers(DeletionEligibilityDto eligibility) {
        try {
            return OBJECT_MAPPER.writeValueAsString(eligibility.getSoftDeleteBlockers());
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize deletion blockers: {}", e.getMessage());
            return "[]";
        }
    }
}
