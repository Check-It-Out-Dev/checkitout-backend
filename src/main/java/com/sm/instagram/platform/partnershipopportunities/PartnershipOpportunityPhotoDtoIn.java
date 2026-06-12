package com.sm.instagram.platform.partnershipopportunities;

import com.sm.instagram.platform.common.util.ValidationPatterns;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PartnershipOpportunityPhotoDtoIn {

    @Schema(description = "Unique identifier of the photo")
    private Long id;

    @NotNull(message = "{validation.photo.url.required}")
    @Pattern(
            regexp = ValidationPatterns.HTTPS_URL_PATTERN,
            message = "URL must use HTTPS protocol and be properly formatted"
    )
    @Schema(description = "Valid URL to the photo resource")
    private String url;

    @NotNull(message = "{validation.photo.orderNumber.required}")
    @Min(value = 0, message = "{validation.photo.orderNumber.min}")
    @Schema(description = "Display order of the photo (0-based)")
    private Integer orderNumber;

    @NotNull(message = "{validation.photo.isCover.required}")
    @Schema(description = "Indicates if this photo is the cover image")
    private Boolean isCover;
}
