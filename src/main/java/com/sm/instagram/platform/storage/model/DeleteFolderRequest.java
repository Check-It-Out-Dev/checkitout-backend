package com.sm.instagram.platform.storage.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Request DTO for folder deletion.
 */
@Data
public class DeleteFolderRequest {
    
    @NotBlank(message = "{validation.file.folderPath.required}")
    private String path;
}
