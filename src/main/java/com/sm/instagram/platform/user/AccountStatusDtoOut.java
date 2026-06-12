package com.sm.instagram.platform.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Output DTO for AccountStatus with translation support
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountStatusDtoOut {
    private String value;        // Original enum value: "ACTIVE", "INACTIVE", etc.
    private String label;        // Translated label: "Active", "Inactive", etc.
    private String description;  // Translated description
    private String originalLabel; // Fallback to enum name if translation missing
    private String colorTheme;   // UI color theme
    private String icon;         // UI icon
    private boolean isActive;    // Helper methods
    private boolean canLogin;
    private boolean isTerminal;
}
