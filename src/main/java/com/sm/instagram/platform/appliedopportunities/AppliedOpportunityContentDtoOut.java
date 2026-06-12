package com.sm.instagram.platform.appliedopportunities;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
public class AppliedOpportunityContentDtoOut {

    private Long id;
    private Long appliedOpportunityId;
    private Long contentTypeId;
    private String contentTypeName;
    private Integer contentCount;
    private List<String> urls;
    private String description;
    private String tags;
    private LocalDateTime contentCreationDate;
    private LocalDateTime submissionDate;
    private ContentApprovalStatus approvalStatus;
    private String approvalNotes;

    // Engagement metrics
    private Long likesCount;
    private Long commentsCount;
    private Long viewsCount;
    private Long sharesCount;

    // Social media link
    private String socialMediaLink;

    // Tracking fields
    private LocalDateTime createdTime;
    private LocalDateTime lastUpdateTime;
    private String updaterId;
}
