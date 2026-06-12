package com.sm.instagram.platform.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response format required by Meta's data deletion callback.
 * Meta expects exactly these two fields.
 */
public record DataDeletionResponse(
        @JsonProperty("url") String url,
        @JsonProperty("confirmation_code") String confirmationCode
) {}
