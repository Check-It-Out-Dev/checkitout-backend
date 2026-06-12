package com.sm.instagram.platform.auth.stepup.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StepUpTokenResponse {
    private boolean success;
    private String token;
    private int expiresInSeconds;
}
