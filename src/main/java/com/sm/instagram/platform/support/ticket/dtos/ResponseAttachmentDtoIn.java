package com.sm.instagram.platform.support.ticket.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO for incoming response attachment data.
 *
 * <p>SECURITY (pentest 3.1 + owner directive 2026-06-13): uploadId-only —
 * see {@link TicketAttachmentDtoIn} for the full rationale. The server
 * derives every file attribute from its own tracking row.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ResponseAttachmentDtoIn {

    /**
     * The tracked upload id returned by {@code POST /upload/signed-url}.
     * Must belong to the calling user.
     */
    @NotBlank(message = "{validation.attachment.uploadId.required}")
    @Size(max = 36, message = "{validation.attachment.uploadId.size}")
    private String uploadId;
}
