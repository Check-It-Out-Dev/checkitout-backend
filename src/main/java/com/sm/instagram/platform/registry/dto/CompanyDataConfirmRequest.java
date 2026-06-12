package com.sm.instagram.platform.registry.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CompanyDataConfirmRequest {

    @NotBlank(message = "{error.registry.nip_required}")
    @Pattern(regexp = "^[0-9]{10}$", message = "{error.registry.invalid_nip}")
    private String nip;

    /** Optional user-provided correspondence address */
    private Map<String, String> correspondenceAddress;
}
