package com.sm.instagram.platform.support.ticket.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO for incoming ticket attachment data.
 *
 * <p>SECURITY (pentest 3.1 + owner directive 2026-06-13): the client no
 * longer supplies ANY file data — only the {@code uploadId} minted by
 * {@code POST /upload/signed-url}. The server resolves the file URL,
 * name, content type and size from its own PostgreSQL tracking row
 * ({@code file_uploads}) after verifying ownership and that the blob
 * really landed in our bucket. A client-supplied URL/type/size was both
 * substitutable (attacker host) and spoofable (lying content type).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TicketAttachmentDtoIn {

    /**
     * The tracked upload id returned by {@code POST /upload/signed-url}.
     * Must belong to the calling user.
     */
    @NotBlank(message = "{validation.attachment.uploadId.required}")
    @Size(max = 36, message = "{validation.attachment.uploadId.size}")
    private String uploadId;
}
