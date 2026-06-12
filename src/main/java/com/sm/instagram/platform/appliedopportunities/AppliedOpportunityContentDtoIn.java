package com.sm.instagram.platform.appliedopportunities;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
public class AppliedOpportunityContentDtoIn {

    @NotNull(message = "{validation.appliedContent.appliedOpportunityId.required}")
    private Long appliedOpportunityId;

    @NotNull(message = "{validation.appliedContent.contentTypeId.required}")
    private Long contentTypeId;

    @Positive(message = "{validation.appliedContent.contentCount.positive}")
    private Integer contentCount;

    private List<String> urls;

    @Size(max = 1000, message = "{validation.appliedContent.description.size}")
    private String description;

    @Size(max = 500, message = "{validation.appliedContent.tags.size}")
    private String tags;

    @Size(max = 1000, message = "{validation.appliedContent.socialMediaLink.size}")
    private String socialMediaLink;

    private LocalDateTime contentCreationDate;

    private LocalDateTime submissionDate;
}
