package com.sm.instagram.platform.common.exceptions;

import java.io.Serializable;

/**
 * Exception for resource not found errors (HTTP 404).
 * <p>
 * Use this exception when a requested resource (user, post, comment, etc.)
 * cannot be found in the database or storage.
 *
 * @example throw new ResourceNotFoundException("error.resource.user_not_found", userId);
 * @example throw new ResourceNotFoundException("error.resource.post_not_found", postId);
 * @example throw new ResourceNotFoundException("error.resource.comment_not_found", commentId);
 */
public class ResourceNotFoundException extends TranslatableException {
    /**
     * Creates a new resource not found exception.
     *
     * @param messageKey The i18n message key (e.g., "error.resource.not_found")
     * @param args       Optional parameters for message formatting
     */
    public ResourceNotFoundException(String messageKey, Serializable... args) {
        super(messageKey, args);
    }

    public ResourceNotFoundException(String resourceName) {
        super("error.resource.not_found", resourceName);
    }

}
