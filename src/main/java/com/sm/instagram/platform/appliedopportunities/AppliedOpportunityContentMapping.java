package com.sm.instagram.platform.appliedopportunities;

import com.sm.instagram.platform.contenttype.ContentType;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class AppliedOpportunityContentMapping {

    public AppliedOpportunityContentDtoOut toDto(AppliedOpportunityContent content) {
        if (content == null) {
            return null;
        }

        AppliedOpportunityContentDtoOut dto = new AppliedOpportunityContentDtoOut();
        dto.setId(content.getId());
        dto.setAppliedOpportunityId(content.getAppliedOpportunity().getId());
        dto.setContentTypeId(content.getContentType().getId());
        dto.setContentTypeName(content.getContentType().getName());
        dto.setContentCount(content.getContentCount());
        dto.setUrls(content.getUrls());
        dto.setDescription(content.getDescription());
        dto.setTags(content.getTags());
        dto.setSocialMediaLink(content.getSocialMediaLink());
        dto.setContentCreationDate(content.getContentCreationDate());
        dto.setSubmissionDate(content.getSubmissionDate());
        dto.setApprovalStatus(content.getApprovalStatus());
        dto.setApprovalNotes(content.getApprovalNotes());
        dto.setLikesCount(content.getLikesCount());
        dto.setCommentsCount(content.getCommentsCount());
        dto.setViewsCount(content.getViewsCount());
        dto.setSharesCount(content.getSharesCount());
        dto.setCreatedTime(content.getCreatedTime());
        dto.setLastUpdateTime(content.getLastUpdateTime());
        dto.setUpdaterId(content.getUpdaterId());
        return dto;
    }
    
    public AppliedOpportunityContentSimpleDtoOut toSimpleDto(AppliedOpportunityContent content) {
        if (content == null) {
            return null;
        }

        AppliedOpportunityContentSimpleDtoOut dto = new AppliedOpportunityContentSimpleDtoOut();
        dto.setId(content.getId());
        dto.setContentTypeName(content.getContentType() != null ? content.getContentType().getName() : null);
        dto.setContentCount(content.getContentCount());
        dto.setUrls(content.getUrls());
        dto.setApprovalStatus(content.getApprovalStatus());
        dto.setSocialMediaLink(content.getSocialMediaLink());
        dto.setSubmissionDate(content.getSubmissionDate());
        dto.setLikesCount(content.getLikesCount());
        dto.setViewsCount(content.getViewsCount());
        return dto;
    }

    public AppliedOpportunityContent fromDto(AppliedOpportunityContentDtoIn dto, AppliedOpportunity appliedOpportunity, ContentType contentType) {
        if (dto == null) {
            return null;
        }

        AppliedOpportunityContent content = new AppliedOpportunityContent();
        content.setAppliedOpportunity(appliedOpportunity);
        content.setContentType(contentType);
        content.setContentCount(dto.getContentCount());
        content.setUrls(dto.getUrls());
        content.setDescription(dto.getDescription());
        content.setTags(dto.getTags());
        content.setSocialMediaLink(dto.getSocialMediaLink());
        content.setContentCreationDate(dto.getContentCreationDate());
        content.setSubmissionDate(dto.getSubmissionDate() != null ? dto.getSubmissionDate() : LocalDateTime.now());
        content.setApprovalStatus(ContentApprovalStatus.PENDING);
        return content;
    }

    public void updateFromDto(AppliedOpportunityContent content, AppliedOpportunityContentDtoIn dto, ContentType contentType) {
        if (content == null || dto == null) {
            return;
        }

        content.setContentType(contentType);
        content.setContentCount(dto.getContentCount());
        content.setUrls(dto.getUrls());
        content.setDescription(dto.getDescription());
        content.setTags(dto.getTags());
        content.setSocialMediaLink(dto.getSocialMediaLink());
        content.setContentCreationDate(dto.getContentCreationDate());
        if (dto.getSubmissionDate() != null) {
            content.setSubmissionDate(dto.getSubmissionDate());
        }
    }
}
