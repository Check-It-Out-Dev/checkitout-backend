package com.sm.instagram.platform.support.ticket.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO for incoming response attachment data.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ResponseAttachmentDtoIn {

    /**
     * Original filename of the uploaded file.
     */
    @NotBlank(message = "{validation.attachment.fileName.required}")
    @Size(max = 255, message = "{validation.attachment.fileName.size}")
    private String fileName;

    /**
     * MIME type of the file.
     */
    @NotBlank(message = "{validation.attachment.contentType.required}")
    @Size(max = 100, message = "{validation.attachment.contentType.size}")
    private String contentType;

    /**
     * URL to the file in Firebase Storage.
     */
    @NotBlank(message = "{validation.attachment.fileUrl.required}")
    @Size(max = 2048, message = "{validation.attachment.fileUrl.size}")
    private String fileUrl;

    /**
     * Size of the file in bytes.
     */
    @Positive(message = "{validation.attachment.fileSize.positive}")
    private Long fileSize;
}