package com.sm.instagram.platform.appliedopportunities;

import org.springframework.security.core.context.SecurityContextHolder;
import com.sm.instagram.platform.common.authorization.PermissionUtils;
import com.sm.instagram.platform.common.ratelimit.RateLimit;
import com.sm.instagram.platform.common.ratelimit.RateLimitKeyType;
import com.sm.instagram.platform.common.ratelimit.RateLimitProfile;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@PreAuthorize("isAuthenticated()")
@RequestMapping("/applied-opportunity/content")
@Slf4j
@RateLimit(profile = RateLimitProfile.STANDARD, keyType = RateLimitKeyType.USER_ENDPOINT)  // 60 req/min for content endpoints
public class AppliedOpportunityContentController {
    private final AppliedOpportunityContentService contentService;
    private final AppliedOpportunityContentMapping contentMapping;
    private final PermissionUtils permissionUtils;

    public AppliedOpportunityContentController(AppliedOpportunityContentService contentService,
                                               AppliedOpportunityContentMapping contentMapping,
                                               PermissionUtils permissionUtils) {
        this.contentService = contentService;
        this.contentMapping = contentMapping;
        this.permissionUtils = permissionUtils;
    }

    // ===== CONTENT SUBMISSION ENDPOINTS =====

    @PostMapping
    @Operation(summary = "Submit new content", description = "Submit content for an applied opportunity (influencer only)")
    public ResponseEntity<AppliedOpportunityContentDtoOut> submitContent(
            @Valid @RequestBody AppliedOpportunityContentDtoIn contentDto) {

        String firebaseUid = SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal().toString();
        
        log.info("GDPR: Operation=submitContent, FirebaseUID={}, AppliedOpportunityID={}, Purpose=content_submission",
                firebaseUid, contentDto.getAppliedOpportunityId());

        // Let the service handle permission validation
        String updaterId = permissionUtils.getUserId();
        AppliedOpportunityContent content = contentService.createContentSubmission(contentDto, updaterId);

        AppliedOpportunityContentDtoOut responseDto = contentMapping.toDto(content);
        log.info("GDPR: DataCreated=content, FirebaseUID={}, ContentID={}, AppliedOpportunityID={}, Purpose=content_created",
                firebaseUid, content.getId(), contentDto.getAppliedOpportunityId());

        return new ResponseEntity<>(responseDto, HttpStatus.CREATED);
    }

    @PutMapping("/{contentId}")
    @Operation(summary = "Update content submission", description = "Update existing content submission (content owner only)")
    public ResponseEntity<AppliedOpportunityContentDtoOut> updateContent(
            @PathVariable Long contentId,
            @Valid @RequestBody AppliedOpportunityContentDtoIn contentDto) {

        String firebaseUid = SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal().toString();
        
        log.info("GDPR: Operation=updateContent, FirebaseUID={}, ContentID={}, Purpose=content_modification",
                firebaseUid, contentId);

        // Let the service handle permission validation
        String updaterId = permissionUtils.getUserId();
        AppliedOpportunityContent content = contentService.updateContentSubmission(contentId, contentDto, updaterId);

        AppliedOpportunityContentDtoOut responseDto = contentMapping.toDto(content);
        log.info("GDPR: DataModified=content, FirebaseUID={}, ContentID={}, Purpose=content_updated",
                firebaseUid, contentId);

        return ResponseEntity.ok(responseDto);
    }

    // ===== CONTENT RETRIEVAL ENDPOINTS =====

    @GetMapping("/{contentId}")
    @Operation(summary = "Get content by ID", description = "Retrieve specific content submission")
    public ResponseEntity<AppliedOpportunityContentDtoOut> getContent(@PathVariable Long contentId) {
        String firebaseUid = SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal().toString();
        
        log.info("GDPR: Operation=getContentById, FirebaseUID={}, ContentID={}, Purpose=content_retrieval",
                firebaseUid, contentId);
        
        // Let the service handle permission validation
        AppliedOpportunityContent content = contentService.getContentById(contentId);
        AppliedOpportunityContentDtoOut responseDto = contentMapping.toDto(content);
        
        log.info("GDPR: DataAccessed=content.all_fields, FirebaseUID={}, ContentID={}, Purpose=display",
                firebaseUid, contentId);
        
        return ResponseEntity.ok(responseDto);
    }

    @GetMapping("/applied-opportunity/{appliedOpportunityId}")
    @Operation(summary = "Get content by applied opportunity", description = "Retrieves content for specific applied opportunity with optional status filtering")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Content retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Applied opportunity not found"),
            @ApiResponse(responseCode = "403", description = "User not authorized to access this content")
    })
    public ResponseEntity<List<AppliedOpportunityContentDtoOut>> getContentByAppliedOpportunity(
            @PathVariable Long appliedOpportunityId,
            @Parameter(description = "Filter by content approval status (PENDING, APPROVED, REJECTED)")
            @RequestParam(required = false) ContentApprovalStatus contentStatus) {

        // Let the service handle permission validation
        List<AppliedOpportunityContent> contents;

        if (contentStatus != null) {
            contents = contentService.getContentByAppliedOpportunityAndStatus(appliedOpportunityId, contentStatus);
        } else {
            contents = contentService.getContentByAppliedOpportunity(appliedOpportunityId);
        }

        List<AppliedOpportunityContentDtoOut> responseDtos = contents.stream()
                .map(contentMapping::toDto)
                .toList();

        return ResponseEntity.ok(responseDtos);
    }

    @GetMapping("/applied-opportunity/{appliedOpportunityId}/paged")
    @Operation(summary = "Get paginated content by applied opportunity", description = "Retrieves paginated content for specific applied opportunity with optional filtering")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Content retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Applied opportunity not found"),
            @ApiResponse(responseCode = "403", description = "User not authorized to access this content")
    })
    public ResponseEntity<Page<AppliedOpportunityContentDtoOut>> getContentByAppliedOpportunityPaged(
            @PathVariable Long appliedOpportunityId,
            Pageable pageable,
            @Parameter(description = "Additional filter parameters") @RequestParam Map<String, String> filters) {

        // Let the service handle permission validation
        Page<AppliedOpportunityContent> page = contentService.getContentByAppliedOpportunityPaged(appliedOpportunityId, pageable, filters);
        Page<AppliedOpportunityContentDtoOut> dtoPage = page.map(contentMapping::toDto);

        return ResponseEntity.ok(dtoPage);
    }

    // ===== ADMIN/COMPANY CONTENT MANAGEMENT ENDPOINTS =====

    @GetMapping("/pending-approval")
    @PreAuthorize("hasAuthority('ADMIN') or hasAuthority('COMPANY')")
    @Operation(summary = "Get pending content", description = "Retrieve all content pending approval (admin/company only)")
    public ResponseEntity<List<AppliedOpportunityContentDtoOut>> getPendingContent() {
        // Let the service handle permission-based filtering
        List<AppliedOpportunityContent> contents = contentService.getContentByApprovalStatus(ContentApprovalStatus.PENDING);

        List<AppliedOpportunityContentDtoOut> responseDtos = contents.stream()
                .map(contentMapping::toDto)
                .toList();

        return ResponseEntity.ok(responseDtos);
    }

    @GetMapping("/status/{status}")
    @PreAuthorize("hasAuthority('ADMIN')")
    @Operation(summary = "Get content by status", description = "Retrieve content by approval status (admin only)")
    public ResponseEntity<List<AppliedOpportunityContentDtoOut>> getContentByStatus(
            @PathVariable ContentApprovalStatus status) {

        List<AppliedOpportunityContent> contents = contentService.getContentByApprovalStatus(status);
        List<AppliedOpportunityContentDtoOut> responseDtos = contents.stream()
                .map(contentMapping::toDto)
                .toList();

        return ResponseEntity.ok(responseDtos);
    }

    // ===== CONTENT APPROVAL ENDPOINTS =====

    @PatchMapping("/{contentId}/approve")
    @PreAuthorize("hasAuthority('ADMIN') or hasAuthority('COMPANY')")
    @Operation(summary = "Approve content", description = "Approve content submission (admin/company only)")
    public ResponseEntity<Void> approveContent(
            @PathVariable Long contentId,
            @RequestParam(required = false) String approvalNotes) {

        String firebaseUid = SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal().toString();
        
        log.warn("GDPR: APPROVAL Operation=approveContent, FirebaseUID={}, ContentID={}, Purpose=content_approval",
                firebaseUid, contentId);
        
        // Let the service handle detailed permission validation
        String updaterId = permissionUtils.getUserId();
        contentService.approveContent(contentId, approvalNotes, updaterId);

        log.info("GDPR: APPROVAL_COMPLETE ContentID={}, FirebaseUID={}, ApprovalNotes={}, Purpose=approved",
                contentId, firebaseUid, approvalNotes != null ? "provided" : "none");
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/{contentId}/reject")
    @PreAuthorize("hasAuthority('ADMIN') or hasAuthority('COMPANY')")
    @Operation(summary = "Reject content", description = "Reject content submission (admin/company only)")
    public ResponseEntity<Void> rejectContent(
            @PathVariable Long contentId,
            @RequestParam(required = false) String approvalNotes) {

        String firebaseUid = SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal().toString();
        
        log.warn("GDPR: REJECTION Operation=rejectContent, FirebaseUID={}, ContentID={}, Purpose=content_rejection",
                firebaseUid, contentId);
        
        // Let the service handle detailed permission validation
        String updaterId = permissionUtils.getUserId();
        contentService.rejectContent(contentId, approvalNotes, updaterId);

        log.info("GDPR: REJECTION_COMPLETE ContentID={}, FirebaseUID={}, RejectionNotes={}, Purpose=rejected",
                contentId, firebaseUid, approvalNotes != null ? "provided" : "none");
        return ResponseEntity.ok().build();
    }

    // ===== ENGAGEMENT METRICS ENDPOINTS =====

    @PatchMapping("/{contentId}/engagement")
    @Operation(summary = "Update engagement metrics", description = "Update engagement metrics for content (content owner only)")
    public ResponseEntity<Void> updateEngagementMetrics(
            @PathVariable Long contentId,
            @RequestParam(required = false) Long likes,
            @RequestParam(required = false) Long comments,
            @RequestParam(required = false) Long views,
            @RequestParam(required = false) Long shares) {

        // Let the service handle permission validation
        String updaterId = permissionUtils.getUserId();
        contentService.updateEngagementMetrics(contentId, likes, comments, views, shares, updaterId);

        log.info("Engagement metrics updated for content {} by user {}", contentId, updaterId);
        return ResponseEntity.ok().build();
    }

    // ===== CONTENT DELETION ENDPOINTS =====

    @DeleteMapping("/{contentId}")
    @Operation(summary = "Delete content", description = "Delete content submission (content owner or admin only)")
    public ResponseEntity<Void> deleteContent(@PathVariable Long contentId) {

        String firebaseUid = SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal().toString();
        
        log.warn("GDPR: DELETION Operation=deleteContent, FirebaseUID={}, ContentID={}, Purpose=user_requested",
                firebaseUid, contentId);
        
        // Let the service handle permission validation
        contentService.deleteContent(contentId);

        log.info("GDPR: DELETION_COMPLETE ContentID={}, FirebaseUID={}, Permanent=true",
                contentId, firebaseUid);
        return ResponseEntity.noContent().build();
    }
}
