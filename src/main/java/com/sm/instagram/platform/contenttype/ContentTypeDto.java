package com.sm.instagram.platform.contenttype;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Inbound DTO used when creating or updating a content type.
 *
 * <p>{@code id} is populated only on update requests; {@code name} is required
 * and bounded at 255 characters to match the persisted column width. Validation
 * messages resolve via {@code messages.properties} so they can be localized.
 */
@Getter
@Setter
public class ContentTypeDto {

    private Long id;

    @NotBlank(message = "{validation.contentType.name.required}")
    @Size(max = 255, message = "{validation.contentType.name.size}")
    private String name;
}
