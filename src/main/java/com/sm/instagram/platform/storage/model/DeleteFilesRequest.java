package com.sm.instagram.platform.storage.model;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * Request DTO for batch file deletion.
 */
@Data
public class DeleteFilesRequest {
    
    @NotEmpty(message = "{validation.file.fileUrls.required}")
    @Size(max = 100, message = "{validation.file.fileUrls.size}")
    private List<String> fileUrls;
}
