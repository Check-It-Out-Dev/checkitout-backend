package com.sm.instagram.platform.platform;

import com.sm.instagram.platform.common.util.ValidationPatterns;
import com.sm.instagram.platform.contenttype.ContentTypeDto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.Set;

/**
 * Bidirectional DTO for {@link Platform} aggregates exposed to admin tooling.
 *
 * <p>Used both as inbound payload (create / update) and outbound representation
 * (read). On inbound flows {@code id} is {@code null} for creates; on update
 * and outbound flows it carries the persisted primary key. The validation
 * matches the entity-level constraints so a request cannot bypass them by
 * binding a DTO directly.
 *
 * <p>{@code logoUrl} is optional but, when present, must be HTTPS — http logos
 * would mixed-content-block on the production site.
 */
@Getter
@Setter
public class PlatformDto {

    private Long id;

    @NotBlank(message = "{validation.platform.name.required}")
    @Size(max = 255, message = "{validation.platform.name.size}")
    private String name;

    @Pattern(
            regexp = ValidationPatterns.HTTPS_URL_PATTERN,
            message = "URL must use HTTPS protocol and be properly formatted"
    )
    @Size(max = 2048, message = "URL cannot exceed 2048 characters")
    private String logoUrl;

    @NotNull(message = "{validation.platform.active.required}")
    private Boolean active;

    @NotNull(message = "{validation.platform.contentTypes.required}")
    private Set<ContentTypeDto> contentTypes;
}
