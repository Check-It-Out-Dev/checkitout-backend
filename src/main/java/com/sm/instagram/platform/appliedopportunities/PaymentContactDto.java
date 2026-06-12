package com.sm.instagram.platform.appliedopportunities;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO for payment contact information at the TO_BE_PAID/DONE stage.
 * CIO-373: Enables payment coordination between brand and influencer.
 *
 * @param name           Full name of the contact
 * @param email          Email address (always included)
 * @param phone          Phone number (only if sharePhoneForPayments is true)
 * @param profilePicture Profile picture URL
 */
@Schema(description = "Contact information for payment coordination")
public record PaymentContactDto(
        @Schema(description = "Full name of the contact")
        String name,

        @Schema(description = "Email address for payment coordination (always visible)")
        String email,

        @Schema(description = "Phone number for payment coordination (only if user has enabled sharing)")
        String phone,

        @Schema(description = "Profile picture URL")
        String profilePicture
) {
}
