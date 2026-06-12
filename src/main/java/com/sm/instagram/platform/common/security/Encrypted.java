package com.sm.instagram.platform.common.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.annotation.Documented;

/**
 * Indicates that a field contains encrypted data that should be handled with care.
 * Fields marked with this annotation are encrypted before storage and decrypted after retrieval.
 * 
 * Security considerations:
 * - Never log the decrypted value
 * - Ensure proper key management via Google Cloud KMS
 * - Monitor decryption failures in production
 * 
 * @since 1.0
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Encrypted {
    /**
     * Optional description of what encryption method is used.
     * Default is Google Cloud KMS.
     */
    String method() default "Google Cloud KMS";
    
    /**
     * Optional key identifier for audit purposes.
     */
    String keyId() default "token-encryption-key";
}
