package com.sm.instagram.platform.userpreferences;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserPreferencesDtoIn {
    private Boolean notificationEmailEnabled;
    private Boolean notificationPushEnabled;
    private Boolean notificationSmsEnabled;
    private Boolean darkModeEnabled;
    private Boolean notificationPartnershipEnabled;
    private Boolean notificationSupportEnabled;
    private Boolean notificationSystemEnabled;
    private Boolean notificationEmailPartnershipEnabled;
    private Boolean notificationEmailSupportEnabled;

    @Schema(description = "Preferred language code", allowableValues = {"pl", "en"}, example = "pl")
    @Size(max = 10, message = "{validation.preferences.language.size}")
    private String language;

    @Size(max = 50, message = "{validation.preferences.timezone.size}")
    private String timezone;

    @Schema(description = "Email notification frequency. Parsed as EmailFrequency enum on the server.",
            allowableValues = {"IMMEDIATE", "HOURLY_DIGEST", "DAILY_DIGEST", "WEEKLY_DIGEST"},
            example = "WEEKLY_DIGEST")
    private String communicationFrequency;
    private Boolean gdprMarketingConsent;
    private Boolean sharePhoneForPayments;
    private Boolean twoFactorAuthenticationEnabled;
}