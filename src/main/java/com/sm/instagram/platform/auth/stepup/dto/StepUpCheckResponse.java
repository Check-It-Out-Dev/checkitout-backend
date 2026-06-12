package com.sm.instagram.platform.auth.stepup.dto;

import com.sm.instagram.platform.auth.stepup.StepUpChallengeType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StepUpCheckResponse {
    private boolean required;
    private StepUpChallengeType challengeType;
}
