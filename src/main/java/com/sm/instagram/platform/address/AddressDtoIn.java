package com.sm.instagram.platform.address;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Data Transfer Object for receiving Address data in API requests.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AddressDtoIn {

    private Long id;
    
    private Long userId;
    
    private Long partnershipOpportunityId;
    
    @NotBlank(message = "{validation.address.street.required}")
    @Size(max = 255, message = "{validation.address.street.size}")
    private String street;

    @NotBlank(message = "{validation.address.city.required}")
    @Size(max = 100, message = "{validation.address.city.size}")
    private String city;

    @NotBlank(message = "{validation.address.postalCode.required}")
    @Size(max = 20, message = "{validation.address.postalCode.size}")
    private String postalCode;

    @NotBlank(message = "{validation.address.country.required}")
    @Size(max = 100, message = "{validation.address.country.size}")
    private String country;

    @Size(max = 100, message = "{validation.address.state.size}")
    private String state;

    @Size(max = 255, message = "{validation.address.additionalInfo.size}")
    private String additionalInfo;

    @Schema(description = "Type of address", defaultValue = "MAIN",
            allowableValues = {"MAIN", "SECONDARY", "BILLING", "SHIPPING", "TEMPORARY"})
    @NotNull(message = "{validation.address.addressType.required}")
    @NotBlank(message = "{validation.address.addressType.required}")
    @Size(max = 50, message = "{validation.address.addressType.size}")
    private String addressType = "MAIN";
    
    private boolean isPrimary = false;

}