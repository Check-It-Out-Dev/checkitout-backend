package com.sm.instagram.platform.auth.dto;

import com.sm.instagram.platform.common.util.ValidationPatterns;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;

/**
 * Unified request DTO for user registration that handles both user types and
 * optional social media connection.
 */
@Data
public class RegisterUserRequest {
    @NotBlank(message = "{validation.email.required}")
    @Email(message = "{validation.email.format}")
    private String email;

    @NotBlank(message = "{validation.password.required}")
    @Size(min = 8, max = 128, message = "{validation.password.size}")
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&]).+$", message = "{validation.password.complexity}")
    private String password;

    private String firstName;

    private String lastName;

    @Schema(allowableValues = {"COMPANY", "INFLUENCER"}, description = "Account type to register")
    @NotBlank(message = "{validation.auth.userType.required}")
    @Pattern(regexp = "COMPANY|INFLUENCER", message = "{validation.auth.userType.pattern}")
    private String userType;

    // Company-specific fields (required if userType is COMPANY)
    private String companyName;

    @Pattern(regexp = "^\\+?[0-9()-]{7,25}$", message = "{validation.phoneNumber.pattern}")
    private String phoneNumber;

    // Address fields for COMPANY users
    private String addressStreet;
    private String addressCity;
    private String addressPostalCode;
    private String addressCountry;
    private String addressState;
    private String addressInfo;

    // Social platform fields (required if userType is INFLUENCER)
    private String socialPlatform;
    private String socialAuthCode;

    @Pattern(regexp = ValidationPatterns.HTTPS_URL_PATTERN, message = "{validation.url.https.pattern}")
    private String profilePictureUrl;

    // Validation method to ensure correct fields based on user type
    @AssertTrue(message = "{validation.auth.companyData.required}")
    public boolean isValidCompanyData() {
        if ("COMPANY".equals(userType)) {
            return companyName != null && !companyName.isEmpty();
        }
        return true;
    }

    @AssertTrue(message = "{validation.auth.addressData.required}")
    public boolean isValidAddressData() {
        if ("COMPANY".equals(userType)) {
            return addressStreet != null && !addressStreet.isEmpty() &&
                    addressCity != null && !addressCity.isEmpty() &&
                    addressPostalCode != null && !addressPostalCode.isEmpty() &&
                    addressCountry != null && !addressCountry.isEmpty();
        }
        return true;
    }

    @AssertTrue(message = "{validation.auth.socialData.required}")
    public boolean isValidSocialData() {
        if ("INFLUENCER".equals(userType) && socialPlatform != null && !socialPlatform.isEmpty()) {
            return socialAuthCode != null && !socialAuthCode.isEmpty();
        }
        // If social platform isn't provided, auth code isn't required
        return true;
    }
}