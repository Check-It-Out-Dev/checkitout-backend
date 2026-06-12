package com.sm.instagram.platform.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompleteVerificationRequest {

    @NotBlank(message = "{validation.auth.oobCode.required}")
    private String oobCode;

    @Size(min = 8, max = 128, message = "{validation.password.size}")
    @Pattern(regexp = "^(?=.*[a-zA-Z])(?=.*\\d).+$", message = "{validation.password.complexity}")
    private String password;
}
