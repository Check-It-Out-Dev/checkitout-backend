package com.sm.instagram.platform.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response for the deletion status check endpoint.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DeletionStatusResponse(
        @JsonProperty("confirmation_code") String confirmationCode,
        @JsonProperty("status") String status,
        @JsonProperty("reason") String reason,
        @JsonProperty("completed_at") String completedAt
) {}
