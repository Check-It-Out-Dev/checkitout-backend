package com.sm.instagram.platform.subscription.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpgradeRequestDtoIn {

    @NotBlank(message = "Target plan name is required")
    private String targetPlan;
}
