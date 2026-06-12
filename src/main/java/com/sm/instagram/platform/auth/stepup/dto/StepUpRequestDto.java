package com.sm.instagram.platform.auth.stepup.dto;

import com.sm.instagram.platform.auth.stepup.StepUpActionType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class StepUpRequestDto {
    @NotNull
    private StepUpActionType actionType;
}
