package com.sm.instagram.platform.auth.dto;

/**
 * Parsed payload from Meta's signed_request callback parameter.
 *
 * @param userId    Instagram app-scoped user ID
 * @param algorithm Signature algorithm (e.g., "HMAC-SHA256")
 * @param issuedAt  Unix timestamp when the request was issued
 */
public record MetaCallbackPayload(
        String userId,
        String algorithm,
        long issuedAt
) {}
