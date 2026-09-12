package com.sm.instagram.platform.appliedopportunities;

import com.sm.instagram.platform.common.authorization.PermissionUtils;
import com.sm.instagram.platform.common.base.BaseService;
import com.sm.instagram.platform.common.exceptions.AuthenticationTranslatableException;
import com.sm.instagram.platform.common.exceptions.BusinessRuleTranslatableException;
import com.sm.instagram.platform.common.exceptions.InsufficientPermissionsException;
import com.sm.instagram.platform.common.exceptions.ResourceNotFoundException;
import com.sm.instagram.platform.common.util.RepositoryResolver;
import com.sm.instagram.platform.contenttype.ContentType;
import com.sm.instagram.platform.contenttype.ContentTypeRepository;
import com.sm.instagram.platform.notification.event.OpportunityStatusChangedEvent;
import com.sm.instagram.platform.user.User;
import com.sm.instagram.platform.user.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@Transactional
public class AppliedOpportunityContentService extends BaseService<AppliedOpportunityContent, Long, AppliedOpportunityContentDtoIn> {

    public static final String APPLIED_OPPORTUNITY_NOT_FOUND = "Applied opportunity not found";
    public static final String CONTENT_SUBMISSION_NOT_FOUND_WITH_ID = "Content submission not found with id: ";
    private final AppliedOpportunityContentRepository contentRepository;
    private final AppliedOpportunityRepository appliedOpportunityRepository;
    private final ContentTypeRepository contentTypeRepository;
    private final PermissionUtils permissionUtils;
    private final ApplicationEventPublisher eventPublisher;
    private final UserRepository userRepository;

    public AppliedOpportunityContentService(AppliedOpportunityContentSpecificationBuilder specificationBuilder,
                                            AppliedOpportunityContentRepository contentRepository,
                                            ModelMapper modelMapper,
                                            RepositoryResolver repositoryResolver,
                                            AppliedOpportunityRepository appliedOpportunityRepository,
                                            ContentTypeRepository contentTypeRepository,
                                            PermissionUtils permissionUtils,
                                            ApplicationContext applicationContext,
                                            ApplicationEventPublisher eventPublisher,
                                            UserRepository userRepository) {
        super(applicationContext, specificationBuilder, contentRepository, modelMapper, repositoryResolver);
        this.contentRepository = contentRepository;
        this.appliedOpportunityRepository = appliedOpportunityRepository;
        this.contentTypeRepository = contentTypeRepository;
        this.permissionUtils = permissionUtils;
        this.eventPublisher = eventPublisher;
        this.userRepository = userRepository;
    }

    @Override
    protected AppliedOpportunityContentService getSelf() {
        return (AppliedOpportunityContentService) super.getSelf();
    }

    @Transactional
    public AppliedOpportunityContent createContentSubmission(AppliedOpportunityContentDtoIn contentDto, String updaterId) {
        log.info("GDPR: Operation=createContentSubmission, FirebaseUID={}, AppliedOpportunityID={}, Purpose=content_creation",
                updaterId, contentDto.getAppliedOpportunityId());

        AppliedOpportunity appliedOpportunity = appliedOpportunityRepository.findById(contentDto.getAppliedOpportunityId())
                .orElseThrow(() -> new ResourceNotFoundException("error.business.item_not_found", "Applied opportunity"));

        // Validate permission: Only admin or opportunity owner (influencer) can submit content
        if (!permissionUtils.isAdmin() && !permissionUtils.isUserOwner(appliedOpportunity)) {
            log.warn("GDPR: AccessDenied Operation=createContentSubmission, FirebaseUID={}, Reason=insufficient_permissions", updaterId);
            throw new InsufficientPermissionsException(
                    "error.auth.insufficient_permissions",
                    updaterId,
                    "createContentSubmission",
                    "AppliedOpportunityContent");
        }

        ContentType contentType = contentTypeRepository.findById(contentDto.getContentTypeId())
                .orElseThrow(() -> new ResourceNotFoundException("error.business.item_not_found", "Content type"));

        AppliedOpportunityContent content = new AppliedOpportunityContent();
        content.setAppliedOpportunity(appliedOpportunity);
        content.setContentType(contentType);
        content.setContentCount(contentDto.getContentCount());
        content.setUrls(contentDto.getUrls());
        content.setDescription(contentDto.getDescription());
        content.setTags(contentDto.getTags());
        content.setSocialMediaLink(contentDto.getSocialMediaLink());
        content.setContentCreationDate(contentDto.getContentCreationDate());
        content.setUpdaterId(updaterId);
        content.setApprovalStatus(ContentApprovalStatus.PENDING);

        content.setSubmissionDate(
                Optional.ofNullable(content.getSubmissionDate())
                        .orElseGet(() -> Optional.ofNullable(contentDto.getSubmissionDate())
                                .orElse(LocalDateTime.now()))
        );

        AppliedOpportunityContent saved = contentRepository.save(content);

        getSelf().moveToContentSendToAccept(appliedOpportunity, updaterId);

        return saved;
    }


    @Transactional
    public AppliedOpportunityContent updateContentSubmission(Long contentId, AppliedOpportunityContentDtoIn contentDto, String updaterId) {
        log.info("GDPR: Operation=updateContentSubmission, FirebaseUID={}, ContentID={}, Purpose=content_modification",
                updaterId, contentId);

        AppliedOpportunityContent content = contentRepository.findById(contentId)
                .orElseThrow(() -> new ResourceNotFoundException("error.business.item_not_found", contentId));

        // Validate permission: Only admin or content owner can update
        if (!permissionUtils.isAdmin() && !permissionUtils.isUserOwner(content.getAppliedOpportunity())) {
            log.warn("GDPR: AccessDenied Operation=updateContentSubmission, FirebaseUID={}, ContentID={}, Reason=insufficient_permissions",
                    updaterId, contentId);
            throw new InsufficientPermissionsException(
                    "error.auth.insufficient_permissions",
                    updaterId,
                    "updateContentSubmission",
                    "AppliedOpportunityContent#" + contentId);
        }

        ContentType contentType = contentTypeRepository.findById(contentDto.getContentTypeId())
                .orElseThrow(() -> new ResourceNotFoundException("error.business.item_not_found", "Content type"));
        boolean isPublishingNow =
                contentDto.getSocialMediaLink() != null
                        && content.getAppliedOpportunity().getOpportunityStatus().canTransitionTo(OpportunityStatus.CONTENT_POSTED);

        if (isPublishingNow) {
            if (content.getSubmissionDate() == null) {
                content.setSubmissionDate(
                        contentDto.getSubmissionDate() != null
                                ? contentDto.getSubmissionDate()
                                : LocalDateTime.now()
                );
            }
            getSelf().moveToContentPosted(content.getAppliedOpportunity(), updaterId);
        }

        // If updating rejected content, reset status to PENDING (resubmission)
        boolean isResubmission = content.getApprovalStatus() == ContentApprovalStatus.REJECTED;
        if (isResubmission) {
            content.setApprovalStatus(ContentApprovalStatus.PENDING);
            content.setApprovalNotes(null);
            getSelf().moveToContentSendToAccept(content.getAppliedOpportunity(), updaterId);
        }

        content.setContentType(contentType);
        content.setContentCount(contentDto.getContentCount());
        content.setUrls(contentDto.getUrls());
        content.setDescription(contentDto.getDescription());
        content.setTags(contentDto.getTags());
        content.setSocialMediaLink(contentDto.getSocialMediaLink());
        content.setContentCreationDate(contentDto.getContentCreationDate());
        content.setUpdaterId(updaterId);

        return contentRepository.save(content);
    }


    @Transactional
    public void moveToContentPosted(
            AppliedOpportunity opportunity,
            String updaterId
    ) {
        if (opportunity.getOpportunityStatus()
                .canTransitionTo(OpportunityStatus.CONTENT_POSTED)) {

            OpportunityStatus previousStatus = opportunity.getOpportunityStatus();
            opportunity.setOpportunityStatus(OpportunityStatus.CONTENT_POSTED);
            opportunity.setUpdaterId(updaterId);
            AppliedOpportunity saved = appliedOpportunityRepository.save(opportunity);

            // Publish event for notification system
            User triggeredBy = userRepository.findByFirebaseUserId(updaterId)
                    .orElseThrow(() -> new ResourceNotFoundException("error.business.item_not_found", "User"));

            eventPublisher.publishEvent(new OpportunityStatusChangedEvent(
                    this,
                    saved,
                    previousStatus,
                    OpportunityStatus.CONTENT_POSTED,
                    triggeredBy,
                    "Content posted to Instagram"
            ));

            log.info("Published OpportunityStatusChangedEvent: appliedOpportunityId={}, {} -> CONTENT_POSTED",
                    saved.getId(), previousStatus);
        }
    }

    @Transactional
    public void moveToContentSendToAccept(
            AppliedOpportunity opportunity,
            String updaterId
    ) {
        if (opportunity.getOpportunityStatus()
                .canTransitionTo(OpportunityStatus.CONTENT_SEND_TO_ACCEPT)) {

            OpportunityStatus previousStatus = opportunity.getOpportunityStatus();
            opportunity.setOpportunityStatus(OpportunityStatus.CONTENT_SEND_TO_ACCEPT);
            opportunity.setUpdaterId(updaterId);
            AppliedOpportunity saved = appliedOpportunityRepository.save(opportunity);

            // Publish event for notification system
            User triggeredBy = userRepository.findByFirebaseUserId(updaterId)
                    .orElseThrow(() -> new ResourceNotFoundException("error.business.item_not_found", "User"));

            eventPublisher.publishEvent(new OpportunityStatusChangedEvent(
                    this,
                    saved,
                    previousStatus,
                    OpportunityStatus.CONTENT_SEND_TO_ACCEPT,
                    triggeredBy,
                    "Content submitted for review"
            ));

            log.info("Published OpportunityStatusChangedEvent: appliedOpportunityId={}, {} -> CONTENT_SEND_TO_ACCEPT",
                    saved.getId(), previousStatus);
        }
    }

    // javasecurity:S5145 reports this publishEvent as a log-injection sink "in dependency": it
    // cannot see where a Spring event goes, so it assumes a logger. Traced, and it is not one.
    // Every use of the note is NotificationEventListener putting it into a notification
    // parameter map (rejectionReason) that is rendered for the person who is meant to read it.
    // The listener's own log lines carry ids and statuses, never the text. Sanitising here would
    // not close a sink, it would replace the line breaks in a human being's rejection reason
    // with question marks on their way to the screen.
    @SuppressWarnings("javasecurity:S5145")
    public void approveContent(Long contentId, String approvalNotes, String updaterId) {
        log.info("GDPR: Operation=approveContent, FirebaseUID={}, ContentID={}, Purpose=content_approval",
                updaterId, contentId);

        // Fetch content with all relationships for permission checking
        AppliedOpportunityContent content = contentRepository.findByIdWithRelationships(contentId)
                .orElseThrow(() -> new ResourceNotFoundException("error.business.item_not_found", contentId));

        // Validate permission: Admin or company owner can approve
        if (!permissionUtils.isAdmin() &&
                !permissionUtils.isCompanyOwnerOfAppliedOpportunity(content.getAppliedOpportunity())) {
            log.warn("GDPR: AccessDenied Operation=approveContent, FirebaseUID={}, ContentID={}, Reason=insufficient_permissions",
                    updaterId, contentId);
            throw new InsufficientPermissionsException(
                    "error.auth.insufficient_permissions",
                    updaterId,
                    "approveContent",
                    "AppliedOpportunityContent#" + contentId);
        }

        if (!content.getApprovalStatus().canTransitionTo(ContentApprovalStatus.APPROVED)) {
            throw new BusinessRuleTranslatableException("error.business.invalid_state");
        }

        content.setApprovalStatus(ContentApprovalStatus.APPROVED);
        content.setApprovalNotes(approvalNotes);
        content.setUpdaterId(updaterId);
        contentRepository.save(content);

        // Update the AppliedOpportunity status to CONTENT_APPROVED
        AppliedOpportunity opportunity = content.getAppliedOpportunity();
        if (opportunity.getOpportunityStatus() == OpportunityStatus.CONTENT_SEND_TO_ACCEPT) {
            OpportunityStatus previousStatus = opportunity.getOpportunityStatus();
            opportunity.setOpportunityStatus(OpportunityStatus.CONTENT_APPROVED);
            opportunity.setUpdaterId(updaterId);
            AppliedOpportunity saved = appliedOpportunityRepository.save(opportunity);

            // Publish event for notification system
            User triggeredBy = userRepository.findByFirebaseUserId(updaterId)
                    .orElseThrow(() -> new ResourceNotFoundException("error.business.item_not_found", "User"));

            eventPublisher.publishEvent(new OpportunityStatusChangedEvent(
                    this,
                    saved,
                    previousStatus,
                    OpportunityStatus.CONTENT_APPROVED,
                    triggeredBy,
                    approvalNotes != null ? approvalNotes : "Content approved"
            ));

            log.info("GDPR: OpportunityStatusUpdated FirebaseUID={}, AppliedOpportunityID={}, NewStatus=CONTENT_APPROVED, Purpose=workflow_transition",
                    updaterId, opportunity.getId());
            log.info("Published OpportunityStatusChangedEvent: appliedOpportunityId={}, {} -> CONTENT_APPROVED",
                    saved.getId(), previousStatus);
        }

        log.info("GDPR: ContentApproved FirebaseUID={}, ContentID={}, ApprovalStatus=APPROVED, Purpose=workflow_management",
                updaterId, contentId);
    }

    // javasecurity:S5145 reports this publishEvent as a log-injection sink "in dependency": it
    // cannot see where a Spring event goes, so it assumes a logger. Traced, and it is not one.
    // Every use of the note is NotificationEventListener putting it into a notification
    // parameter map (rejectionReason) that is rendered for the person who is meant to read it.
    // The listener's own log lines carry ids and statuses, never the text. Sanitising here would
    // not close a sink, it would replace the line breaks in a human being's rejection reason
    // with question marks on their way to the screen.
    @SuppressWarnings("javasecurity:S5145")
    public void rejectContent(Long contentId, String approvalNotes, String updaterId) {
        log.info("GDPR: Operation=rejectContent, FirebaseUID={}, ContentID={}, Purpose=content_rejection",
                updaterId, contentId);

        // Fetch content with all relationships for permission checking
        AppliedOpportunityContent content = contentRepository.findByIdWithRelationships(contentId)
                .orElseThrow(() -> new ResourceNotFoundException("error.business.item_not_found", contentId));

        // Validate permission: Admin or company owner can reject
        if (!permissionUtils.isAdmin() &&
                !permissionUtils.isCompanyOwnerOfAppliedOpportunity(content.getAppliedOpportunity())) {
            log.warn("GDPR: AccessDenied Operation=rejectContent, FirebaseUID={}, ContentID={}, Reason=insufficient_permissions",
                    updaterId, contentId);
            throw new InsufficientPermissionsException(
                    "error.auth.insufficient_permissions",
                    updaterId,
                    "rejectContent",
                    "AppliedOpportunityContent#" + contentId);
        }

        if (content.getApprovalStatus() == ContentApprovalStatus.REJECTED) {
            // Idempotent: already rejected, just update notes if provided
            if (approvalNotes != null) {
                content.setApprovalNotes(approvalNotes);
                content.setUpdaterId(updaterId);
                contentRepository.save(content);
            }
            log.info("Content {} already REJECTED, idempotent reject (notes updated: {})", contentId, approvalNotes != null);
            return;
        }
        if (!content.getApprovalStatus().canTransitionTo(ContentApprovalStatus.REJECTED)) {
            throw new BusinessRuleTranslatableException("error.business.invalid_state");
        }

        content.setApprovalStatus(ContentApprovalStatus.REJECTED);
        content.setApprovalNotes(approvalNotes);
        content.setUpdaterId(updaterId);
        contentRepository.save(content);

        // Get triggering user for event
        User triggeredBy = userRepository.findByFirebaseUserId(updaterId)
                .orElseThrow(() -> new ResourceNotFoundException("error.business.item_not_found", "User"));

        // Update the AppliedOpportunity status based on current status
        AppliedOpportunity opportunity = content.getAppliedOpportunity();
        if (opportunity.getOpportunityStatus() == OpportunityStatus.CONTENT_SEND_TO_ACCEPT) {
            // Vimeo content review rejection (before Instagram posting)
            OpportunityStatus previousStatus = opportunity.getOpportunityStatus();
            opportunity.setOpportunityStatus(OpportunityStatus.CONTENT_REJECTED);
            opportunity.setUpdaterId(updaterId);
            AppliedOpportunity saved = appliedOpportunityRepository.save(opportunity);

            // Publish event for notification system
            eventPublisher.publishEvent(new OpportunityStatusChangedEvent(
                    this,
                    saved,
                    previousStatus,
                    OpportunityStatus.CONTENT_REJECTED,
                    triggeredBy,
                    approvalNotes != null ? approvalNotes : "Content rejected"
            ));

            log.info("GDPR: OpportunityStatusUpdated FirebaseUID={}, AppliedOpportunityID={}, NewStatus=CONTENT_REJECTED, Purpose=workflow_transition",
                    updaterId, opportunity.getId());
            log.info("Published OpportunityStatusChangedEvent: appliedOpportunityId={}, {} -> CONTENT_REJECTED",
                    saved.getId(), previousStatus);
        } else if (opportunity.getOpportunityStatus() == OpportunityStatus.CONTENT_POSTED) {
            // Instagram content validation rejection (after Instagram posting)
            OpportunityStatus previousStatus = opportunity.getOpportunityStatus();
            opportunity.setOpportunityStatus(OpportunityStatus.CONTENT_POSTED_REJECTED);
            opportunity.setUpdaterId(updaterId);
            AppliedOpportunity saved = appliedOpportunityRepository.save(opportunity);

            // Publish event for notification system
            eventPublisher.publishEvent(new OpportunityStatusChangedEvent(
                    this,
                    saved,
                    previousStatus,
                    OpportunityStatus.CONTENT_POSTED_REJECTED,
                    triggeredBy,
                    approvalNotes != null ? approvalNotes : "Posted content rejected"
            ));

            log.info("GDPR: OpportunityStatusUpdated FirebaseUID={}, AppliedOpportunityID={}, NewStatus=CONTENT_POSTED_REJECTED, Purpose=workflow_transition",
                    updaterId, opportunity.getId());
            log.info("Published OpportunityStatusChangedEvent: appliedOpportunityId={}, {} -> CONTENT_POSTED_REJECTED",
                    saved.getId(), previousStatus);
        }

        log.info("GDPR: ContentRejected FirebaseUID={}, ContentID={}, ApprovalStatus=REJECTED, Purpose=workflow_management",
                updaterId, contentId);
    }

    public List<AppliedOpportunityContent> getContentByAppliedOpportunity(Long appliedOpportunityId) {
        String currentUserId = permissionUtils.getUserId();
        log.info("GDPR: Operation=getContentByAppliedOpportunity, FirebaseUID={}, AppliedOpportunityID={}, Purpose=data_retrieval",
                currentUserId, appliedOpportunityId);
        // Validate access to the applied opportunity
        AppliedOpportunity opportunity = appliedOpportunityRepository.findById(appliedOpportunityId)
                .orElseThrow(() -> new ResourceNotFoundException("error.business.item_not_found", "opportunity"));

        if (!permissionUtils.canViewAppliedOpportunity(opportunity)) {
            log.warn("GDPR: AccessDenied Operation=getContentByAppliedOpportunity, FirebaseUID={}, AppliedOpportunityID={}, Reason=no_view_permission",
                    currentUserId, appliedOpportunityId);
            throw new InsufficientPermissionsException(
                    "error.auth.insufficient_permissions",
                    currentUserId,
                    "getContentByAppliedOpportunity",
                    "AppliedOpportunity#" + appliedOpportunityId);
        }

        List<AppliedOpportunityContent> contents = contentRepository.findByAppliedOpportunityId(appliedOpportunityId);
        log.info("GDPR: DataAccessed=applied_opportunity_content, FirebaseUID={}, RecordCount={}, Purpose=content_listing",
                currentUserId, contents.size());
        return contents;
    }

    public List<AppliedOpportunityContent> getContentByAppliedOpportunityAndStatus(Long appliedOpportunityId, ContentApprovalStatus status) {
        String currentUserId = permissionUtils.getUserId();
        log.info("GDPR: Operation=getContentByAppliedOpportunityAndStatus, FirebaseUID={}, AppliedOpportunityID={}, Status={}, Purpose=filtered_retrieval",
                currentUserId, appliedOpportunityId, status);
        // Validate access to the applied opportunity
        AppliedOpportunity opportunity = appliedOpportunityRepository.findById(appliedOpportunityId)
                .orElseThrow(() -> new ResourceNotFoundException("error.business.item_not_found", "opportunity"));

        if (!permissionUtils.canViewAppliedOpportunity(opportunity)) {
            log.warn("GDPR: AccessDenied Operation=getContentByAppliedOpportunityAndStatus, FirebaseUID={}, AppliedOpportunityID={}, Reason=no_view_permission",
                    currentUserId, appliedOpportunityId);
            throw new InsufficientPermissionsException(
                    "error.auth.insufficient_permissions",
                    currentUserId,
                    "getContentByAppliedOpportunityAndStatus",
                    "AppliedOpportunity#" + appliedOpportunityId);
        }

        List<AppliedOpportunityContent> contents = contentRepository.findByAppliedOpportunityIdAndApprovalStatus(appliedOpportunityId, status);
        log.info("GDPR: DataAccessed=applied_opportunity_content, FirebaseUID={}, RecordCount={}, FilterStatus={}, Purpose=filtered_content",
                currentUserId, contents.size(), status);
        return contents;
    }

    public Page<AppliedOpportunityContent> getContentByAppliedOpportunityPaged(Long appliedOpportunityId, Pageable pageable, Map<String, String> filters) {
        String currentUserId = permissionUtils.getUserId();
        log.info("GDPR: Operation=getContentByAppliedOpportunityPaged, FirebaseUID={}, AppliedOpportunityID={}, Purpose=paged_retrieval",
                currentUserId, appliedOpportunityId);
        // Validate access to the applied opportunity
        AppliedOpportunity opportunity = appliedOpportunityRepository.findById(appliedOpportunityId)
                .orElseThrow(() -> new ResourceNotFoundException("error.business.item_not_found", "opportunity"));

        if (!permissionUtils.canViewAppliedOpportunity(opportunity)) {
            log.warn("GDPR: AccessDenied Operation=getContentByAppliedOpportunityPaged, FirebaseUID={}, AppliedOpportunityID={}, Reason=no_view_permission",
                    currentUserId, appliedOpportunityId);
            throw new InsufficientPermissionsException(
                    "error.auth.insufficient_permissions",
                    currentUserId,
                    "getContentByAppliedOpportunityPaged",
                    "AppliedOpportunity#" + appliedOpportunityId);
        }

        // Add appliedOpportunityId filter to the filters map
        filters.put("appliedOpportunity.id", appliedOpportunityId.toString());
        Page<AppliedOpportunityContent> page = getSelf().getDataPagedAndFiltered(pageable, filters);
        log.info("GDPR: DataAccessed=applied_opportunity_content, FirebaseUID={}, PageSize={}, TotalElements={}, Purpose=paged_listing",
                currentUserId, page.getSize(), page.getTotalElements());
        return page;
    }

    public List<AppliedOpportunityContent> getContentByApprovalStatus(ContentApprovalStatus status) {
        String currentUserId = permissionUtils.getUserId();
        log.info("GDPR: Operation=getContentByApprovalStatus, FirebaseUID={}, Status={}, Purpose=status_filtering",
                currentUserId, status);

        List<AppliedOpportunityContent> contents = contentRepository.findByApprovalStatus(status);

        // Filter based on permissions
        if (!permissionUtils.isAdmin()) {
            if (permissionUtils.isCompany()) {
                // Companies see only content for their opportunities
                contents = contents.stream()
                        .filter(content -> content.getAppliedOpportunity()
                                .getPartnershipOpportunity()
                                .getCompany()
                                .getFirebaseUserId()
                                .equals(currentUserId))
                        .toList();
            } else if (permissionUtils.isInfluencer()) {
                // Influencers see only their own content
                contents = contents.stream()
                        .filter(content -> content.getAppliedOpportunity()
                                .getInfluencer()
                                .getFirebaseUserId()
                                .equals(currentUserId))
                        .toList();
            } else {
                // Unknown role - return empty list
                contents = List.of();
            }
        }

        log.info("GDPR: DataAccessed=applied_opportunity_content, FirebaseUID={}, RecordCount={}, FilteredByRole={}, Purpose=role_based_content",
                currentUserId, contents.size(), !permissionUtils.isAdmin());
        return contents;
    }

    public void updateEngagementMetrics(Long contentId, Long likes, Long comments, Long views, Long shares, String updaterId) {
        log.info("GDPR: Operation=updateEngagementMetrics, FirebaseUID={}, ContentID={}, Purpose=metrics_update",
                updaterId, contentId);

        AppliedOpportunityContent content = contentRepository.findById(contentId)
                .orElseThrow(() -> new ResourceNotFoundException("error.business.item_not_found", contentId));

        // Validate permission: Only admin or content owner can update metrics
        if (!permissionUtils.isAdmin() && !permissionUtils.isUserOwner(content.getAppliedOpportunity())) {
            log.warn("GDPR: AccessDenied Operation=updateEngagementMetrics, FirebaseUID={}, ContentID={}, Reason=insufficient_permissions",
                    updaterId, contentId);
            throw new InsufficientPermissionsException(
                    "error.auth.insufficient_permissions",
                    updaterId,
                    "updateEngagementMetrics",
                    "AppliedOpportunityContent#" + contentId);
        }

        if (likes != null) content.setLikesCount(likes);
        if (comments != null) content.setCommentsCount(comments);
        if (views != null) content.setViewsCount(views);
        if (shares != null) content.setSharesCount(shares);
        content.setUpdaterId(updaterId);

        contentRepository.save(content);
        log.info("GDPR: MetricsUpdated FirebaseUID={}, ContentID={}, DataUpdated=likes,comments,views,shares, Purpose=engagement_tracking",
                updaterId, contentId);
    }

    public AppliedOpportunityContent getContentById(Long contentId) {
        String currentUserId = permissionUtils.getUserId();
        log.info("GDPR: Operation=getContentById, FirebaseUID={}, ContentID={}, Purpose=single_content_retrieval",
                currentUserId, contentId);

        // Fetch-join contentType + the permission chain: the controller maps this
        // entity to a DTO OUTSIDE any tx and reads contentType.getName(), so a plain
        // findById returned a lazy proxy and this endpoint 500ed with LazyInit.
        AppliedOpportunityContent content = contentRepository.findByIdWithRelationships(contentId)
                .orElseThrow(() -> new ResourceNotFoundException("error.business.item_not_found", contentId));

        // Validate view access
        if (!permissionUtils.canViewAppliedOpportunity(content.getAppliedOpportunity())) {
            log.warn("GDPR: AccessDenied Operation=getContentById, FirebaseUID={}, ContentID={}, Reason=no_view_permission",
                    currentUserId, contentId);
            throw new InsufficientPermissionsException(
                    "error.auth.insufficient_permissions",
                    currentUserId,
                    "getContentById",
                    "AppliedOpportunityContent#" + contentId);
        }

        log.info("GDPR: DataAccessed=applied_opportunity_content.all_fields, FirebaseUID={}, ContentID={}, Purpose=detailed_view",
                currentUserId, contentId);
        return content;
    }

    public void deleteContent(Long contentId) {
        String currentUserId = permissionUtils.getUserId();
        log.warn("GDPR: DELETION Operation=deleteContent, FirebaseUID={}, ContentID={}, Purpose=user_requested_deletion",
                currentUserId, contentId);

        AppliedOpportunityContent content = contentRepository.findById(contentId)
                .orElseThrow(() -> new ResourceNotFoundException("error.business.item_not_found", contentId));

        // Log data that will be deleted for GDPR compliance
        log.info("GDPR: PreDeletion DataSnapshot ContentID={}, FirebaseUID={}, AppliedOpportunityID={}, ContentType={}, Purpose=deletion_audit",
                contentId, currentUserId, content.getAppliedOpportunity().getId(), content.getContentType().getId());

        // Validate permission: Only admin or content owner can delete
        if (!permissionUtils.isAdmin() && !permissionUtils.isUserOwner(content.getAppliedOpportunity())) {
            log.warn("GDPR: AccessDenied Operation=deleteContent, FirebaseUID={}, ContentID={}, Reason=insufficient_permissions",
                    currentUserId, contentId);
            throw new InsufficientPermissionsException(
                    "error.auth.insufficient_permissions",
                    currentUserId,
                    "deleteContent",
                    "AppliedOpportunityContent#" + contentId);
        }

        contentRepository.delete(content);
        log.warn("GDPR: DELETION_COMPLETE ContentID={}, FirebaseUID={}, Permanent=true, Purpose=content_removal",
                contentId, currentUserId);
    }

    /**
     * Retrieves a paginated list of entities as DTOs with all conversions done within transaction.
     * This prevents LazyInitializationException by ensuring all DTO mappings happen inside @Transactional.
     *
     * @param pageable Pagination parameters
     * @param filters  Filter parameters
     * @param <DTOOUT> The output DTO type
     * @return Page of DTOs with all lazy relationships properly loaded
     */
    @Override
    public <DTOOUT> Page<DTOOUT> getDataPagedAndFilteredAsDtos(Pageable pageable, Map<String, String> filters) {
        // Get entities with pagination via proxy to ensure transactional behavior
        Page<AppliedOpportunityContent> page = getSelf().getDataPagedAndFiltered(pageable, filters);

        // Convert to DTOs within transaction
        Page<AppliedOpportunityContentDtoOut> dtoPage = page.map(entity -> {
            AppliedOpportunityContentDtoOut dto = new AppliedOpportunityContentDtoOut();

            // Map basic fields
            dto.setId(entity.getId());
            dto.setContentCount(entity.getContentCount());
            dto.setUrls(entity.getUrls());
            dto.setDescription(entity.getDescription());
            dto.setTags(entity.getTags());
            dto.setContentCreationDate(entity.getContentCreationDate());
            dto.setSubmissionDate(entity.getSubmissionDate());
            dto.setApprovalStatus(entity.getApprovalStatus());
            dto.setApprovalNotes(entity.getApprovalNotes());

            // Engagement metrics
            dto.setLikesCount(entity.getLikesCount());
            dto.setCommentsCount(entity.getCommentsCount());
            dto.setViewsCount(entity.getViewsCount());
            dto.setSharesCount(entity.getSharesCount());

            // Social media link
            dto.setSocialMediaLink(entity.getSocialMediaLink());

            // Tracking fields
            dto.setCreatedTime(entity.getCreatedTime());
            dto.setLastUpdateTime(entity.getLastUpdateTime());
            dto.setUpdaterId(entity.getUpdaterId());

            // Handle lazy relationships safely
            if (entity.getAppliedOpportunity() != null) {
                dto.setAppliedOpportunityId(entity.getAppliedOpportunity().getId());
            }

            if (entity.getContentType() != null) {
                dto.setContentTypeId(entity.getContentType().getId());
                // Force initialization of contentType name
                dto.setContentTypeName(entity.getContentType().getName());
            }

            return dto;
        });

        @SuppressWarnings("unchecked")
        Page<DTOOUT> result = (Page<DTOOUT>) dtoPage;
        return result;
    }

    /**
     * Converts an entity to DTO within a transaction.
     * This method ensures all lazy collections are properly handled.
     *
     * @param entity the entity to convert
     * @return DTO with all data properly mapped
     */
    @Override
    @Transactional(readOnly = true)
    public <DTOOUT> DTOOUT toDto(AppliedOpportunityContent entity) {
        if (entity == null) {
            return null;
        }

        @SuppressWarnings("unchecked")
        DTOOUT result = (DTOOUT) modelMapper.map(entity, AppliedOpportunityContentDtoOut.class);
        return result;
    }

    /**
     * Creates a new entity from DTO and returns as DTO.
     * All conversions happen within transaction boundary.
     *
     * @param dto The input DTO
     * @return The created entity as DTO
     */
    @Override
    @Transactional
    public <DTOOUT> DTOOUT createFromDtoAsDto(AppliedOpportunityContentDtoIn dto) {
        AppliedOpportunityContent entity = modelMapper.map(dto, AppliedOpportunityContent.class);
        AppliedOpportunityContent saved = getSelf().save(entity);
        return getSelf().toDto(saved);
    }
}
