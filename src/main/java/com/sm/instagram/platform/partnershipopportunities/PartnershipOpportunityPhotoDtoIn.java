package com.sm.instagram.platform.partnershipopportunities;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Photo reference on campaign create/update.
 *
 * <p>Exactly one of {@code id} / {@code uploadId} identifies the photo
 * (enforced in the service):
 * <ul>
 *   <li><b>{@code id}</b> — an EXISTING photo of this campaign, kept /
 *       reordered / re-flagged in place. Its stored URL is never touched.</li>
 *   <li><b>{@code uploadId}</b> — a NEW photo, referenced by the tracked
 *       upload id from {@code POST /upload/signed-url}. The server resolves
 *       the URL from its own tracking row (pentest 3.1 + owner directive
 *       2026-06-13: the client never supplies a file URL).</li>
 * </ul>
 */
@Getter
@Setter
public class PartnershipOpportunityPhotoDtoIn {

    @Schema(description = "Identifier of an EXISTING photo to keep (mutually exclusive with uploadId)")
    private Long id;

    @Size(max = 36, message = "{validation.attachment.uploadId.size}")
    @Schema(description = "Tracked upload id from POST /upload/signed-url for a NEW photo (mutually exclusive with id)")
    private String uploadId;

    @NotNull(message = "{validation.photo.orderNumber.required}")
    @Min(value = 0, message = "{validation.photo.orderNumber.min}")
    @Schema(description = "Display order of the photo (0-based)")
    private Integer orderNumber;

    @NotNull(message = "{validation.photo.isCover.required}")
    @Schema(description = "Indicates if this photo is the cover image")
    private Boolean isCover;
}
