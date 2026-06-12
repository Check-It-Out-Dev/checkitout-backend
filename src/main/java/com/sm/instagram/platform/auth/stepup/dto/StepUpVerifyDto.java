package com.sm.instagram.platform.auth.stepup.dto;

import com.sm.instagram.platform.auth.stepup.StepUpActionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class StepUpVerifyDto {
    @NotNull
    private StepUpActionType actionType;

    @NotBlank
    @Pattern(regexp = "\\d{6}")
    private String code;
}
