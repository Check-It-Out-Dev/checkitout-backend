package com.sm.instagram.platform.storage.model;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Request DTO for single file deletion.
 */
@Data
public class DeleteFileRequest {
    
    @NotNull(message = "{validation.file.fileUrl.required}")
    private String fileUrl;
}
