package com.sm.instagram.platform.common.exceptions;

import java.io.Serializable;

/**
 * Exception for file storage and file system errors.
 * <p>
 * Use this exception when file upload fails, file is too large,
 * unsupported file type is detected, or storage operations fail.
 *
 * @example throw new StorageTranslatableException("error.storage.file_too_large", "10MB");
 * @example throw new StorageTranslatableException("error.storage.unsupported_type", "exe");
 * @example throw new StorageTranslatableException("error.storage.upload_failed", "profile.jpg");
 */
public class StorageTranslatableException extends TranslatableException {
    /**
     * Creates a new storage exception.
     *
     * @param messageKey The i18n message key (e.g., "error.storage.disk_full")
     * @param args       Optional parameters for message formatting
     */
    public StorageTranslatableException(String messageKey, Serializable... args) {
        super(messageKey, args);
    }
}
