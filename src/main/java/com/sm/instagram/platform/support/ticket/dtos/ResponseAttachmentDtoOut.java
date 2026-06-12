package com.sm.instagram.platform.support.ticket.dtos;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * DTO for outgoing response attachment data.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ResponseAttachmentDtoOut {

    /**
     * Unique ID of the attachment.
     */
    private Long id;

    /**
     * ID of the response this attachment belongs to.
     */
    private Long responseId;

    /**
     * Original filename of the uploaded file.
     */
    private String fileName;

    /**
     * MIME type of the file.
     */
    private String contentType;

    /**
     * Size of the file in bytes.
     */
    private Long fileSize;

    /**
     * When this attachment was uploaded.
     */
    private LocalDateTime uploadTime;

    /**
     * URL to download the attachment.
     */
    private String downloadUrl;
}