package com.sm.instagram.platform.admin.cascade;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sm.instagram.platform.admin.cascade.dto.*;
import com.sm.instagram.platform.appliedopportunities.*;
import com.sm.instagram.platform.auth.cache.UserCacheService;
import com.sm.instagram.platform.auth.firebase.FirebaseService;
import com.sm.instagram.platform.auth.firebase.FirestoreService;
import com.sm.instagram.platform.auth.firebase.TotpFirestoreService;
import com.sm.instagram.platform.common.exceptions.BusinessRuleTranslatableException;
import com.sm.instagram.platform.common.exceptions.ResourceNotFoundException;
import com.sm.instagram.platform.partnershipopportunities.PartnershipOpportunity;
import com.sm.instagram.platform.partnershipopportunities.PartnershipOpportunityRepository;
import com.sm.instagram.platform.storage.service.FirebaseStorageService;
import com.sm.instagram.platform.user.User;
import com.sm.instagram.platform.user.UserRepository;
import com.sm.instagram.platform.user.UserType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementation of admin cascade delete service.
 * Orchestrates deletion across PostgreSQL, Firestore, and Firebase Auth with retry support.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AdminCascadeDeleteServiceImpl implements AdminCascadeDeleteService {

    private static final int MAX_RETRIES = 3;
    private static final String ENTITY_TYPE_USER = "USER";
    private static final String ENTITY_TYPE_PARTNERSHIP_OPPORTUNITY = "PARTNERSHIP_OPPORTUNITY";
    private static final String ENTITY_TYPE_APPLIED_OPPORTUNITY = "APPLIED_OPPORTUNITY";

    private final UserRepository userRepository;
    private final PartnershipOpportunityRepository partnershipOpportunityRepository;
    private final AppliedOpportunityRepository appliedOpportunityRepository;
    private final AppliedOpportunityStatusHistoryRepository statusHistoryRepository;
    private final AppliedOpportunityContentRepository contentRepository;
    private final CascadeDeleteTaskRepository taskRepository;
    private final FirebaseService firebaseService;
    private final FirestoreService firestoreService;
    private final TotpFirestoreService totpFirestoreService;
    private final FirebaseStorageService firebaseStorageService;
    private final UserCacheService userCacheService;
    private final ObjectMapper objectMapper;

    // ========== User Deletion ==========

    @Override
    @Transactional(readOnly = true)
    public CascadeDeletePreview previewUserDeletion(Long userId) {
        log.info("ADMIN_CASCADE: Previewing user deletion, userId={}", userId);

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        String firebaseUid = user.getFirebaseUserId();
        List<EntityTypeCount> entityBreakdown = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> systemsToClean = new ArrayList<>();
        int totalEntityCount = 1; // The user itself

        // Always have PostgreSQL
        systemsToClean.add("PostgreSQL");

        // Count related entities based on user type
        if (user.getUserType() == UserType.COMPANY) {
            // Count partnership opportunities
            int poCount = partnershipOpportunityRepository.countByCompanyId(userId);
            if (poCount > 0) {
                entityBreakdown.add(EntityTypeCount.builder()
                    .entityType("PartnershipOpportunity")
                    .count(poCount)
                    .description("Partnership opportunities created by this company")
                    .build());
                totalEntityCount += poCount;

                // Count applied opportunities to company's POs
                List<PartnershipOpportunity> pos = partnershipOpportunityRepository.findByCompanyId(userId);
                int aoCount = 0;
                int historyCount = 0;
                int contentCount = 0;

                for (PartnershipOpportunity po : pos) {
                    List<AppliedOpportunity> aos = appliedOpportunityRepository.findByPartnershipOpportunityId(po.getId());
                    aoCount += aos.size();
                    for (AppliedOpportunity ao : aos) {
                        historyCount += statusHistoryRepository.countByAppliedOpportunity_Id(ao.getId());
                        Long contentCountLong = contentRepository.countByAppliedOpportunityId(ao.getId());
                        contentCount += contentCountLong != null ? contentCountLong.intValue() : 0;
                    }
                }

                if (aoCount > 0) {
                    entityBreakdown.add(EntityTypeCount.builder()
                        .entityType("AppliedOpportunity")
                        .count(aoCount)
                        .description("Applications to company's opportunities")
                        .build());
                    totalEntityCount += aoCount;
                }
                if (historyCount > 0) {
                    entityBreakdown.add(EntityTypeCount.builder()
                        .entityType("AppliedOpportunityStatusHistory")
                        .count(historyCount)
                        .description("Status change history records")
                        .build());
                    totalEntityCount += historyCount;
                }
                if (contentCount > 0) {
                    entityBreakdown.add(EntityTypeCount.builder()
                        .entityType("AppliedOpportunityContent")
                        .count(contentCount)
                        .description("Content submissions")
                        .build());
                    totalEntityCount += contentCount;
                }
            }
        } else if (user.getUserType() == UserType.INFLUENCER) {
            // Count applied opportunities by this influencer
            int aoCount = appliedOpportunityRepository.countByInfluencerId(userId);
            if (aoCount > 0) {
                entityBreakdown.add(EntityTypeCount.builder()
                    .entityType("AppliedOpportunity")
                    .count(aoCount)
                    .description("Applications submitted by this influencer")
                    .build());
                totalEntityCount += aoCount;

                // Count history and content for influencer's AOs
                List<AppliedOpportunity> aos = appliedOpportunityRepository.findByInfluencerId(userId);
                int historyCount = 0;
                int contentCount = 0;
                for (AppliedOpportunity ao : aos) {
                    historyCount += statusHistoryRepository.countByAppliedOpportunity_Id(ao.getId());
                    Long contentCountLong = contentRepository.countByAppliedOpportunityId(ao.getId());
                    contentCount += contentCountLong != null ? contentCountLong.intValue() : 0;
                }

                if (historyCount > 0) {
                    entityBreakdown.add(EntityTypeCount.builder()
                        .entityType("AppliedOpportunityStatusHistory")
                        .count(historyCount)
                        .description("Status change history records")
                        .build());
                    totalEntityCount += historyCount;
                }
                if (contentCount > 0) {
                    entityBreakdown.add(EntityTypeCount.builder()
                        .entityType("AppliedOpportunityContent")
                        .count(contentCount)
                        .description("Content submissions")
                        .build());
                    totalEntityCount += contentCount;
                }
            }
        }

        // Count addresses (cascade automatically via JPA)
        int addressCount = user.getAddresses() != null ? user.getAddresses().size() : 0;
        if (addressCount > 0) {
            entityBreakdown.add(EntityTypeCount.builder()
                .entityType("Address")
                .count(addressCount)
                .description("User addresses")
                .build());
            totalEntityCount += addressCount;
        }

        // Count social connections (cascade automatically via JPA)
        int socialCount = user.getSocialConnections() != null ? user.getSocialConnections().size() : 0;
        if (socialCount > 0) {
            entityBreakdown.add(EntityTypeCount.builder()
                .entityType("UserSocialConnection")
                .count(socialCount)
                .description("Social media connections")
                .build());
            totalEntityCount += socialCount;
        }

        // Check Firebase/Firestore systems
        boolean hasFirestoreInstagram = false;
        boolean hasFirestoreTotp = false;
        boolean existsInFirebaseAuth = false;

        if (firebaseUid != null && !firebaseUid.isEmpty()) {
            hasFirestoreInstagram = firestoreService.existsInstagramUserData(firebaseUid);
            hasFirestoreTotp = totpFirestoreService.totpSecretExists(firebaseUid);
            existsInFirebaseAuth = firebaseService.userExists(firebaseUid);

            if (hasFirestoreInstagram) {
                systemsToClean.add("Firestore (instagramUsers)");
            }
            if (hasFirestoreTotp) {
                systemsToClean.add("Firestore (totpSecrets)");
            }
            // Always add Firebase Storage since we can't easily check for user files
            systemsToClean.add("Firebase Storage");
            if (existsInFirebaseAuth) {
                systemsToClean.add("Firebase Auth");
            } else {
                warnings.add("User not found in Firebase Auth - may have been manually deleted");
            }
        } else {
            warnings.add("User has no firebaseUserId - Firebase/Firestore cleanup will be skipped");
        }

        // Check for last admin
        if (user.getUserType() == UserType.ADMIN) {
            long adminCount = userRepository.countByUserTypeAndAccountStatusNot(UserType.ADMIN, com.sm.instagram.platform.user.AccountStatus.TO_BE_DELETED);
            if (adminCount <= 1) {
                warnings.add("WARNING: This is the last admin account - deletion is blocked!");
            }
        }

        return CascadeDeletePreview.builder()
            .userId(userId)
            .firebaseUserId(firebaseUid)
            .userEmail(user.getEmail())
            .userType(user.getUserType().name())
            .userName(user.getName() != null ? user.getName() : user.getFirstName() + " " + user.getLastName())
            .totalEntityCount(totalEntityCount)
            .entityBreakdown(entityBreakdown)
            .systemsToClean(systemsToClean)
            .warnings(warnings)
            .hasFirestoreInstagramData(hasFirestoreInstagram)
            .hasFirestoreTotpData(hasFirestoreTotp)
            .hasFirebaseStorageData(true) // Assume yes, can't easily check
            .existsInFirebaseAuth(existsInFirebaseAuth)
            .confirmationCode("CASCADE-DELETE-" + userId)
            .build();
    }

    @Override
    @Transactional
    public CascadeDeleteResult forceDeleteUser(Long userId, String reason, String adminFirebaseId) {
        log.warn("ADMIN_CASCADE: DELETION Starting force delete for user, userId={}, admin={}, reason={}",
            userId, adminFirebaseId, reason);

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        String firebaseUid = user.getFirebaseUserId();

        // Check for last admin
        if (user.getUserType() == UserType.ADMIN) {
            long adminCount = userRepository.countByUserTypeAndAccountStatusNot(UserType.ADMIN, com.sm.instagram.platform.user.AccountStatus.TO_BE_DELETED);
            if (adminCount <= 1) {
                throw new BusinessRuleTranslatableException("error.cascade.last_admin");
            }
        }

        // Create task for tracking
        CascadeDeleteTask task = CascadeDeleteTask.builder()
            .userId(userId)
            .firebaseUserId(firebaseUid)
            .entityType(ENTITY_TYPE_USER)
            .reason(reason)
            .adminFirebaseId(adminFirebaseId)
            .lastAttemptAt(LocalDateTime.now())
            .build();

        CascadeDeleteResult.CascadeDeleteResultBuilder resultBuilder = CascadeDeleteResult.builder()
            .userId(userId)
            .firebaseUserId(firebaseUid)
            .startedAt(LocalDateTime.now());

        List<EntityTypeCount> deletedByType = new ArrayList<>();
        int totalDeleted = 0;

        try {
            // 1. Delete PostgreSQL data in correct FK order
            totalDeleted = deletePostgresqlUserCascade(user, deletedByType);
            task.setPostgresqlStatus(SystemStatus.SUCCESS);
            log.info("ADMIN_CASCADE: PostgreSQL deletion complete for user {}, entities deleted: {}", userId, totalDeleted);

        } catch (Exception e) {
            log.error("ADMIN_CASCADE: PostgreSQL deletion FAILED for user {}: {}", userId, e.getMessage(), e);
            task.setPostgresqlStatus(SystemStatus.FAILED);
            task.setErrorDetails(buildErrorJson("postgresql", e.getMessage()));
            taskRepository.save(task);
            throw e; // Rollback transaction
        }

        // Save task after PostgreSQL success (non-transactional cleanup follows)
        task = taskRepository.save(task);

        // 2. Non-transactional Firebase/Firestore cleanup
        List<String> warnings = new ArrayList<>();

        if (firebaseUid != null && !firebaseUid.isEmpty()) {
            // Firestore Instagram
            try {
                boolean deleted = firestoreService.deleteInstagramUserData(firebaseUid);
                task.setFirestoreInstagramStatus(deleted ? SystemStatus.SUCCESS : SystemStatus.SKIPPED);
            } catch (Exception e) {
                log.error("ADMIN_CASCADE: Firestore Instagram cleanup FAILED for {}: {}", firebaseUid, e.getMessage());
                task.setFirestoreInstagramStatus(SystemStatus.FAILED);
                appendError(task, "firestoreInstagram", e.getMessage());
                warnings.add("Firestore Instagram cleanup failed: " + e.getMessage());
            }

            // Firestore TOTP
            try {
                totpFirestoreService.deleteTotpData(firebaseUid);
                task.setFirestoreTotpStatus(SystemStatus.SUCCESS);
            } catch (Exception e) {
                log.error("ADMIN_CASCADE: Firestore TOTP cleanup FAILED for {}: {}", firebaseUid, e.getMessage());
                task.setFirestoreTotpStatus(SystemStatus.FAILED);
                appendError(task, "firestoreTotp", e.getMessage());
                warnings.add("Firestore TOTP cleanup failed: " + e.getMessage());
            }

            // Firebase Storage
            try {
                firebaseStorageService.deleteUserDirectory(firebaseUid);
                task.setFirebaseStorageStatus(SystemStatus.SUCCESS);
            } catch (Exception e) {
                log.error("ADMIN_CASCADE: Firebase Storage cleanup FAILED for {}: {}", firebaseUid, e.getMessage());
                task.setFirebaseStorageStatus(SystemStatus.FAILED);
                appendError(task, "firebaseStorage", e.getMessage());
                warnings.add("Firebase Storage cleanup failed: " + e.getMessage());
            }

            // Firebase Auth (last)
            FirebaseService.DeleteResult authResult = firebaseService.deleteUserGraceful(firebaseUid);
            switch (authResult) {
                case SUCCESS -> task.setFirebaseAuthStatus(SystemStatus.SUCCESS);
                case SKIPPED -> {
                    task.setFirebaseAuthStatus(SystemStatus.SKIPPED);
                    warnings.add("Firebase Auth user was already deleted or not found");
                }
                case FAILED -> {
                    task.setFirebaseAuthStatus(SystemStatus.FAILED);
                    appendError(task, "firebaseAuth", "Failed to delete Firebase Auth user");
                    warnings.add("Firebase Auth deletion failed");
                }
            }
        } else {
            // No Firebase ID - skip all Firebase cleanup
            task.setFirestoreInstagramStatus(SystemStatus.SKIPPED);
            task.setFirestoreTotpStatus(SystemStatus.SKIPPED);
            task.setFirebaseStorageStatus(SystemStatus.SKIPPED);
            task.setFirebaseAuthStatus(SystemStatus.SKIPPED);
            warnings.add("User had no firebaseUserId - Firebase cleanup skipped");
        }

        // Evict from cache (uses Firebase UID as key)
        if (firebaseUid != null && !firebaseUid.isEmpty()) {
            userCacheService.evict(firebaseUid);
        }

        // Mark completed if all done
        task.markCompletedIfDone();
        taskRepository.save(task);

        return resultBuilder
            .success(task.isFullyCompleted())
            .taskId(task.getId())
            .totalDeleted(totalDeleted)
            .deletedByType(deletedByType)
            .systemStatuses(buildSystemStatuses(task))
            .warnings(warnings)
            .completedAt(LocalDateTime.now())
            .canRetry(task.canRetry(MAX_RETRIES))
            .build();
    }

    /**
     * Delete all PostgreSQL entities related to a user in correct FK order.
     */
    private int deletePostgresqlUserCascade(User user, List<EntityTypeCount> deletedByType) {
        int totalDeleted = 0;
        Long userId = user.getId();

        if (user.getUserType() == UserType.COMPANY) {
            // Delete company's POs and all related AOs
            List<PartnershipOpportunity> pos = partnershipOpportunityRepository.findByCompanyId(userId);

            for (PartnershipOpportunity po : pos) {
                totalDeleted += deletePartnershipOpportunityCascade(po.getId(), deletedByType);
            }
        } else if (user.getUserType() == UserType.INFLUENCER) {
            // Delete influencer's AOs
            List<AppliedOpportunity> aos = appliedOpportunityRepository.findByInfluencerId(userId);

            for (AppliedOpportunity ao : aos) {
                totalDeleted += deleteAppliedOpportunityCascade(ao.getId(), deletedByType);
            }
        }

        // Addresses and SocialConnections cascade via JPA
        int addressCount = user.getAddresses() != null ? user.getAddresses().size() : 0;
        int socialCount = user.getSocialConnections() != null ? user.getSocialConnections().size() : 0;

        // Delete the user
        userRepository.delete(user);
        totalDeleted += 1 + addressCount + socialCount;

        addEntityCount(deletedByType, "User", 1, "User account");
        if (addressCount > 0) {
            addEntityCount(deletedByType, "Address", addressCount, "User addresses");
        }
        if (socialCount > 0) {
            addEntityCount(deletedByType, "UserSocialConnection", socialCount, "Social connections");
        }

        return totalDeleted;
    }

    /**
     * Delete all entities related to a partnership opportunity.
     */
    private int deletePartnershipOpportunityCascade(Long poId, List<EntityTypeCount> deletedByType) {
        int totalDeleted = 0;

        // Delete all AOs for this PO
        List<AppliedOpportunity> aos = appliedOpportunityRepository.findByPartnershipOpportunityId(poId);
        for (AppliedOpportunity ao : aos) {
            totalDeleted += deleteAppliedOpportunityCascade(ao.getId(), deletedByType);
        }

        // Photos cascade via JPA orphanRemoval
        PartnershipOpportunity po = partnershipOpportunityRepository.findById(poId).orElse(null);
        int photoCount = po != null && po.getPhotos() != null ? po.getPhotos().size() : 0;

        // Delete the PO
        partnershipOpportunityRepository.deleteById(poId);
        totalDeleted += 1 + photoCount;

        addEntityCount(deletedByType, "PartnershipOpportunity", 1, "Partnership opportunity");
        if (photoCount > 0) {
            addEntityCount(deletedByType, "PartnershipOpportunityPhoto", photoCount, "Opportunity photos");
        }

        return totalDeleted;
    }

    /**
     * Delete all entities related to an applied opportunity.
     */
    private int deleteAppliedOpportunityCascade(Long aoId, List<EntityTypeCount> deletedByType) {
        int totalDeleted = 0;

        // Delete status history first (no cascade configured)
        List<AppliedOpportunityStatusHistory> history = statusHistoryRepository.findByAppliedOpportunityIdOrderByChangedAtDesc(aoId);
        int historyCount = history.size();
        if (historyCount > 0) {
            statusHistoryRepository.deleteByAppliedOpportunityId(aoId);
            totalDeleted += historyCount;
            addEntityCount(deletedByType, "AppliedOpportunityStatusHistory", historyCount, "Status history");
        }

        // Content cascades via JPA but we count it
        Long contentCountLong = contentRepository.countByAppliedOpportunityId(aoId);
        int contentCount = contentCountLong != null ? contentCountLong.intValue() : 0;

        // Delete the AO (content cascades)
        appliedOpportunityRepository.deleteById(aoId);
        totalDeleted += 1 + contentCount;

        addEntityCount(deletedByType, "AppliedOpportunity", 1, "Applied opportunity");
        if (contentCount > 0) {
            addEntityCount(deletedByType, "AppliedOpportunityContent", contentCount, "Content submissions");
        }

        return totalDeleted;
    }

    private void addEntityCount(List<EntityTypeCount> list, String type, int count, String description) {
        // Merge counts if type already exists
        for (EntityTypeCount etc : list) {
            if (etc.getEntityType().equals(type)) {
                etc.setCount(etc.getCount() + count);
                return;
            }
        }
        list.add(EntityTypeCount.builder()
            .entityType(type)
            .count(count)
            .description(description)
            .build());
    }

    @Override
    @Transactional(readOnly = true)
    public BatchCascadeDeletePreview previewBatchUserDeletion(List<Long> userIds) {
        if (userIds.size() > 10) {
            throw new BusinessRuleTranslatableException("error.cascade.batch_limit");
        }

        List<CascadeDeletePreview> previews = new ArrayList<>();
        List<Long> blockedUserIds = new ArrayList<>();
        List<String> blockedReasons = new ArrayList<>();
        int totalEntityCount = 0;
        Set<String> allSystems = new LinkedHashSet<>();
        List<String> allWarnings = new ArrayList<>();

        for (Long userId : userIds) {
            try {
                CascadeDeletePreview preview = previewUserDeletion(userId);
                previews.add(preview);
                totalEntityCount += preview.getTotalEntityCount();
                allSystems.addAll(preview.getSystemsToClean());

                // Check for blocking conditions
                if (preview.getWarnings().stream().anyMatch(w -> w.contains("last admin"))) {
                    blockedUserIds.add(userId);
                    blockedReasons.add("User " + userId + " is the last admin");
                }

                allWarnings.addAll(preview.getWarnings().stream()
                    .map(w -> "User " + userId + ": " + w)
                    .collect(Collectors.toList()));
            } catch (ResourceNotFoundException e) {
                blockedUserIds.add(userId);
                blockedReasons.add("User " + userId + " not found");
            }
        }

        // Aggregate entity breakdown
        Map<String, EntityTypeCount> aggregated = new LinkedHashMap<>();
        for (CascadeDeletePreview preview : previews) {
            for (EntityTypeCount etc : preview.getEntityBreakdown()) {
                aggregated.merge(etc.getEntityType(),
                    etc,
                    (a, b) -> EntityTypeCount.builder()
                        .entityType(a.getEntityType())
                        .count(a.getCount() + b.getCount())
                        .description(a.getDescription())
                        .build());
            }
        }

        return BatchCascadeDeletePreview.builder()
            .userPreviews(previews)
            .totalEntityCount(totalEntityCount)
            .totalBreakdown(new ArrayList<>(aggregated.values()))
            .allSystemsToClean(new ArrayList<>(allSystems))
            .allWarnings(allWarnings)
            .deletableUserCount(previews.size() - blockedUserIds.size())
            .blockedUserCount(blockedUserIds.size())
            .blockedUserIds(blockedUserIds)
            .blockedReasons(blockedReasons)
            .build();
    }

    @Override
    @Transactional
    public List<CascadeDeleteResult> forceDeleteUsers(List<Long> userIds, String reason, String adminFirebaseId) {
        if (userIds.size() > 10) {
            throw new BusinessRuleTranslatableException("error.cascade.batch_limit");
        }

        List<CascadeDeleteResult> results = new ArrayList<>();
        for (Long userId : userIds) {
            try {
                CascadeDeleteResult result = forceDeleteUser(userId, reason, adminFirebaseId);
                results.add(result);
            } catch (Exception e) {
                log.error("ADMIN_CASCADE: Batch delete failed for user {}: {}", userId, e.getMessage());
                results.add(CascadeDeleteResult.builder()
                    .success(false)
                    .userId(userId)
                    .warnings(List.of("Deletion failed: " + e.getMessage()))
                    .build());
            }
        }
        return results;
    }

    // ========== Partnership Opportunity Deletion ==========

    @Override
    @Transactional(readOnly = true)
    public CascadeDeletePreview previewPartnershipOpportunityDeletion(Long partnershipOpportunityId) {
        PartnershipOpportunity po = partnershipOpportunityRepository.findById(partnershipOpportunityId)
            .orElseThrow(() -> new ResourceNotFoundException("Partnership opportunity not found: " + partnershipOpportunityId));

        List<EntityTypeCount> entityBreakdown = new ArrayList<>();
        int totalEntityCount = 1; // The PO itself

        // Count photos
        int photoCount = po.getPhotos() != null ? po.getPhotos().size() : 0;
        if (photoCount > 0) {
            entityBreakdown.add(EntityTypeCount.builder()
                .entityType("PartnershipOpportunityPhoto")
                .count(photoCount)
                .description("Opportunity photos")
                .build());
            totalEntityCount += photoCount;
        }

        // Count applied opportunities
        List<AppliedOpportunity> aos = appliedOpportunityRepository.findByPartnershipOpportunityId(partnershipOpportunityId);
        if (!aos.isEmpty()) {
            entityBreakdown.add(EntityTypeCount.builder()
                .entityType("AppliedOpportunity")
                .count(aos.size())
                .description("Applications to this opportunity")
                .build());
            totalEntityCount += aos.size();

            int historyCount = 0;
            int contentCount = 0;
            for (AppliedOpportunity ao : aos) {
                historyCount += statusHistoryRepository.countByAppliedOpportunity_Id(ao.getId());
                Long contentCountLong = contentRepository.countByAppliedOpportunityId(ao.getId());
                contentCount += contentCountLong != null ? contentCountLong.intValue() : 0;
            }

            if (historyCount > 0) {
                entityBreakdown.add(EntityTypeCount.builder()
                    .entityType("AppliedOpportunityStatusHistory")
                    .count(historyCount)
                    .description("Status history records")
                    .build());
                totalEntityCount += historyCount;
            }
            if (contentCount > 0) {
                entityBreakdown.add(EntityTypeCount.builder()
                    .entityType("AppliedOpportunityContent")
                    .count(contentCount)
                    .description("Content submissions")
                    .build());
                totalEntityCount += contentCount;
            }
        }

        return CascadeDeletePreview.builder()
            .userId(po.getCompany().getId())
            .userType("COMPANY")
            .userName(po.getName())
            .totalEntityCount(totalEntityCount)
            .entityBreakdown(entityBreakdown)
            .systemsToClean(List.of("PostgreSQL"))
            .warnings(List.of())
            .confirmationCode("CASCADE-DELETE-PO-" + partnershipOpportunityId)
            .build();
    }

    @Override
    @Transactional
    public CascadeDeleteResult forceDeletePartnershipOpportunity(Long partnershipOpportunityId, String reason, String adminFirebaseId) {
        log.warn("ADMIN_CASCADE: DELETION Starting force delete for PO, poId={}, admin={}", partnershipOpportunityId, adminFirebaseId);

        PartnershipOpportunity po = partnershipOpportunityRepository.findById(partnershipOpportunityId)
            .orElseThrow(() -> new ResourceNotFoundException("Partnership opportunity not found: " + partnershipOpportunityId));

        CascadeDeleteTask task = CascadeDeleteTask.builder()
            .userId(po.getCompany().getId())
            .entityType(ENTITY_TYPE_PARTNERSHIP_OPPORTUNITY)
            .reason(reason)
            .adminFirebaseId(adminFirebaseId)
            .lastAttemptAt(LocalDateTime.now())
            .firestoreInstagramStatus(SystemStatus.SKIPPED)
            .firestoreTotpStatus(SystemStatus.SKIPPED)
            .firebaseStorageStatus(SystemStatus.SKIPPED)
            .firebaseAuthStatus(SystemStatus.SKIPPED)
            .build();

        List<EntityTypeCount> deletedByType = new ArrayList<>();
        int totalDeleted;

        try {
            totalDeleted = deletePartnershipOpportunityCascade(partnershipOpportunityId, deletedByType);
            task.setPostgresqlStatus(SystemStatus.SUCCESS);
            task.markCompletedIfDone();
        } catch (Exception e) {
            task.setPostgresqlStatus(SystemStatus.FAILED);
            task.setErrorDetails(buildErrorJson("postgresql", e.getMessage()));
            taskRepository.save(task);
            throw e;
        }

        taskRepository.save(task);

        return CascadeDeleteResult.builder()
            .success(true)
            .taskId(task.getId())
            .totalDeleted(totalDeleted)
            .deletedByType(deletedByType)
            .systemStatuses(buildSystemStatuses(task))
            .completedAt(LocalDateTime.now())
            .build();
    }

    // ========== Applied Opportunity Deletion ==========

    @Override
    @Transactional(readOnly = true)
    public CascadeDeletePreview previewAppliedOpportunityDeletion(Long appliedOpportunityId) {
        AppliedOpportunity ao = appliedOpportunityRepository.findById(appliedOpportunityId)
            .orElseThrow(() -> new ResourceNotFoundException("Applied opportunity not found: " + appliedOpportunityId));

        List<EntityTypeCount> entityBreakdown = new ArrayList<>();
        int totalEntityCount = 1;

        int historyCount = statusHistoryRepository.countByAppliedOpportunity_Id(appliedOpportunityId);
        if (historyCount > 0) {
            entityBreakdown.add(EntityTypeCount.builder()
                .entityType("AppliedOpportunityStatusHistory")
                .count(historyCount)
                .description("Status history records")
                .build());
            totalEntityCount += historyCount;
        }

        Long contentCountLong = contentRepository.countByAppliedOpportunityId(appliedOpportunityId);
        int contentCount = contentCountLong != null ? contentCountLong.intValue() : 0;
        if (contentCount > 0) {
            entityBreakdown.add(EntityTypeCount.builder()
                .entityType("AppliedOpportunityContent")
                .count(contentCount)
                .description("Content submissions")
                .build());
            totalEntityCount += contentCount;
        }

        return CascadeDeletePreview.builder()
            .userId(ao.getInfluencer().getId())
            .userType("INFLUENCER")
            .totalEntityCount(totalEntityCount)
            .entityBreakdown(entityBreakdown)
            .systemsToClean(List.of("PostgreSQL"))
            .warnings(List.of())
            .confirmationCode("CASCADE-DELETE-AO-" + appliedOpportunityId)
            .build();
    }

    @Override
    @Transactional
    public CascadeDeleteResult forceDeleteAppliedOpportunity(Long appliedOpportunityId, String reason, String adminFirebaseId) {
        log.warn("ADMIN_CASCADE: DELETION Starting force delete for AO, aoId={}, admin={}", appliedOpportunityId, adminFirebaseId);

        AppliedOpportunity ao = appliedOpportunityRepository.findById(appliedOpportunityId)
            .orElseThrow(() -> new ResourceNotFoundException("Applied opportunity not found: " + appliedOpportunityId));

        CascadeDeleteTask task = CascadeDeleteTask.builder()
            .userId(ao.getInfluencer().getId())
            .entityType(ENTITY_TYPE_APPLIED_OPPORTUNITY)
            .reason(reason)
            .adminFirebaseId(adminFirebaseId)
            .lastAttemptAt(LocalDateTime.now())
            .firestoreInstagramStatus(SystemStatus.SKIPPED)
            .firestoreTotpStatus(SystemStatus.SKIPPED)
            .firebaseStorageStatus(SystemStatus.SKIPPED)
            .firebaseAuthStatus(SystemStatus.SKIPPED)
            .build();

        List<EntityTypeCount> deletedByType = new ArrayList<>();
        int totalDeleted;

        try {
            totalDeleted = deleteAppliedOpportunityCascade(appliedOpportunityId, deletedByType);
            task.setPostgresqlStatus(SystemStatus.SUCCESS);
            task.markCompletedIfDone();
        } catch (Exception e) {
            task.setPostgresqlStatus(SystemStatus.FAILED);
            task.setErrorDetails(buildErrorJson("postgresql", e.getMessage()));
            taskRepository.save(task);
            throw e;
        }

        taskRepository.save(task);

        return CascadeDeleteResult.builder()
            .success(true)
            .taskId(task.getId())
            .totalDeleted(totalDeleted)
            .deletedByType(deletedByType)
            .systemStatuses(buildSystemStatuses(task))
            .completedAt(LocalDateTime.now())
            .build();
    }

    // ========== Task Management ==========

    @Override
    @Transactional(readOnly = true)
    public Page<CascadeDeleteTaskDto> getPendingTasks(Pageable pageable) {
        return taskRepository.findByCompletedAtIsNull(pageable)
            .map(this::toTaskDto);
    }

    @Override
    @Transactional
    public CascadeDeleteResult retryTask(Long taskId) {
        CascadeDeleteTask task = taskRepository.findById(taskId)
            .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + taskId));

        if (!task.canRetry(MAX_RETRIES)) {
            throw new BusinessRuleTranslatableException("error.cascade.max_retries");
        }

        task.incrementRetry();
        List<String> warnings = new ArrayList<>();

        String firebaseUid = task.getFirebaseUserId();
        if (firebaseUid != null) {
            // Retry failed Firebase/Firestore operations
            if (task.getFirestoreInstagramStatus() == SystemStatus.FAILED) {
                try {
                    boolean deleted = firestoreService.deleteInstagramUserData(firebaseUid);
                    task.setFirestoreInstagramStatus(deleted ? SystemStatus.SUCCESS : SystemStatus.SKIPPED);
                } catch (Exception e) {
                    warnings.add("Firestore Instagram retry failed: " + e.getMessage());
                }
            }

            if (task.getFirestoreTotpStatus() == SystemStatus.FAILED) {
                try {
                    totpFirestoreService.deleteTotpData(firebaseUid);
                    task.setFirestoreTotpStatus(SystemStatus.SUCCESS);
                } catch (Exception e) {
                    warnings.add("Firestore TOTP retry failed: " + e.getMessage());
                }
            }

            if (task.getFirebaseStorageStatus() == SystemStatus.FAILED) {
                try {
                    firebaseStorageService.deleteUserDirectory(firebaseUid);
                    task.setFirebaseStorageStatus(SystemStatus.SUCCESS);
                } catch (Exception e) {
                    warnings.add("Firebase Storage retry failed: " + e.getMessage());
                }
            }

            if (task.getFirebaseAuthStatus() == SystemStatus.FAILED) {
                FirebaseService.DeleteResult result = firebaseService.deleteUserGraceful(firebaseUid);
                switch (result) {
                    case SUCCESS -> task.setFirebaseAuthStatus(SystemStatus.SUCCESS);
                    case SKIPPED -> task.setFirebaseAuthStatus(SystemStatus.SKIPPED);
                    case FAILED -> warnings.add("Firebase Auth retry failed");
                }
            }
        }

        task.markCompletedIfDone();
        taskRepository.save(task);

        return CascadeDeleteResult.builder()
            .success(task.isFullyCompleted())
            .taskId(taskId)
            .systemStatuses(buildSystemStatuses(task))
            .warnings(warnings)
            .canRetry(task.canRetry(MAX_RETRIES))
            .build();
    }

    @Override
    @Transactional(readOnly = true)
    public CascadeDeleteTaskDto getTask(Long taskId) {
        return taskRepository.findById(taskId)
            .map(this::toTaskDto)
            .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + taskId));
    }

    // ========== Orphan Cleanup ==========

    @Override
    @Transactional
    public CascadeDeleteResult deleteOrphanedFirebaseUser(String firebaseUid, String reason, String adminFirebaseId) {
        log.warn("ADMIN_CASCADE: DELETION Cleaning orphaned Firebase user, uid={}", firebaseUid);

        // Verify user doesn't exist in PostgreSQL
        Optional<User> pgUser = userRepository.findByFirebaseUserId(firebaseUid);
        if (pgUser.isPresent()) {
            throw new BusinessRuleTranslatableException("error.cascade.not_orphan");
        }

        CascadeDeleteTask task = CascadeDeleteTask.builder()
            .firebaseUserId(firebaseUid)
            .entityType(ENTITY_TYPE_USER)
            .reason(reason + " (orphaned Firebase user)")
            .adminFirebaseId(adminFirebaseId)
            .lastAttemptAt(LocalDateTime.now())
            .postgresqlStatus(SystemStatus.SKIPPED)
            .build();

        List<String> warnings = new ArrayList<>();

        // Firestore Instagram
        try {
            boolean deleted = firestoreService.deleteInstagramUserData(firebaseUid);
            task.setFirestoreInstagramStatus(deleted ? SystemStatus.SUCCESS : SystemStatus.SKIPPED);
        } catch (Exception e) {
            task.setFirestoreInstagramStatus(SystemStatus.FAILED);
            warnings.add("Firestore Instagram cleanup failed");
        }

        // Firestore TOTP
        try {
            totpFirestoreService.deleteTotpData(firebaseUid);
            task.setFirestoreTotpStatus(SystemStatus.SUCCESS);
        } catch (Exception e) {
            task.setFirestoreTotpStatus(SystemStatus.FAILED);
            warnings.add("Firestore TOTP cleanup failed");
        }

        // Firebase Storage
        try {
            firebaseStorageService.deleteUserDirectory(firebaseUid);
            task.setFirebaseStorageStatus(SystemStatus.SUCCESS);
        } catch (Exception e) {
            task.setFirebaseStorageStatus(SystemStatus.FAILED);
            warnings.add("Firebase Storage cleanup failed");
        }

        // Firebase Auth
        FirebaseService.DeleteResult authResult = firebaseService.deleteUserGraceful(firebaseUid);
        switch (authResult) {
            case SUCCESS -> task.setFirebaseAuthStatus(SystemStatus.SUCCESS);
            case SKIPPED -> task.setFirebaseAuthStatus(SystemStatus.SKIPPED);
            case FAILED -> {
                task.setFirebaseAuthStatus(SystemStatus.FAILED);
                warnings.add("Firebase Auth deletion failed");
            }
        }

        task.markCompletedIfDone();
        taskRepository.save(task);

        return CascadeDeleteResult.builder()
            .success(task.isFullyCompleted())
            .taskId(task.getId())
            .firebaseUserId(firebaseUid)
            .systemStatuses(buildSystemStatuses(task))
            .warnings(warnings)
            .canRetry(task.canRetry(MAX_RETRIES))
            .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> findOrphanedFirebaseUsers(int limit) {
        // This is limited by Firebase API - we can't list all users easily
        // For now, return empty list - admin would need to manually identify orphans
        log.info("ADMIN_CASCADE: Orphan detection requested, limit={}", limit);
        return List.of();
    }

    // ========== Helper Methods ==========

    private SystemStatuses buildSystemStatuses(CascadeDeleteTask task) {
        return SystemStatuses.builder()
            .postgresql(task.getPostgresqlStatus())
            .firestoreInstagram(task.getFirestoreInstagramStatus())
            .firestoreTotp(task.getFirestoreTotpStatus())
            .firebaseStorage(task.getFirebaseStorageStatus())
            .firebaseAuth(task.getFirebaseAuthStatus())
            .build();
    }

    private CascadeDeleteTaskDto toTaskDto(CascadeDeleteTask task) {
        return CascadeDeleteTaskDto.builder()
            .taskId(task.getId())
            .userId(task.getUserId())
            .firebaseUserId(task.getFirebaseUserId())
            .entityType(task.getEntityType())
            .reason(task.getReason())
            .adminFirebaseId(task.getAdminFirebaseId())
            .systemStatuses(buildSystemStatuses(task))
            .retryCount(task.getRetryCount())
            .archiveUrl(task.getArchiveUrl())
            .errorDetails(task.getErrorDetails())
            .createdAt(task.getCreatedAt())
            .lastAttemptAt(task.getLastAttemptAt())
            .completedAt(task.getCompletedAt())
            .canRetry(task.canRetry(MAX_RETRIES))
            .isFullyCompleted(task.isFullyCompleted())
            .build();
    }

    private String buildErrorJson(String system, String message) {
        try {
            Map<String, String> errors = new HashMap<>();
            errors.put(system, message);
            return objectMapper.writeValueAsString(errors);
        } catch (Exception e) {
            return "{\"" + system + "\": \"" + message + "\"}";
        }
    }

    private void appendError(CascadeDeleteTask task, String system, String message) {
        try {
            Map<String, String> errors;
            if (task.getErrorDetails() != null) {
                errors = objectMapper.readValue(task.getErrorDetails(),
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, String>>() {});
            } else {
                errors = new HashMap<>();
            }
            errors.put(system, message);
            task.setErrorDetails(objectMapper.writeValueAsString(errors));
        } catch (Exception e) {
            task.setErrorDetails(buildErrorJson(system, message));
        }
    }
}
