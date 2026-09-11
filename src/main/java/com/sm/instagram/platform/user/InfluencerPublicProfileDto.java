package com.sm.instagram.platform.user;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public non-sealed class InfluencerPublicProfileDto implements PublicProfileDto {

    /** Which of the two shapes this is; see {@link PublicProfileDto}. */
    @Schema(allowableValues = "INFLUENCER", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String profileType = "INFLUENCER";

    private Long id;
    private String name;
    private String profilePicture;
    private LocalDateTime createdTime;
    private String platformName;
    private String displayName;
    private String profileUrl;
    private AccountStatusDtoOut accountStatus;
    private Integer followersCount;
    private Boolean premium;
}