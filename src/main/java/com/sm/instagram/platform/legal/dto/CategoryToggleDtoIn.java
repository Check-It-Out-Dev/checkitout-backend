package com.sm.instagram.platform.legal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CategoryToggleDtoIn {

    @NotBlank(message = "Category type is required")
    @Pattern(regexp = "^[A-Z_]+$", message = "Category type must be uppercase letters and underscores")
    private String categoryType;

    @NotNull(message = "Enabled flag is required")
    private Boolean enabled;

    private Boolean isTrusted;
}
