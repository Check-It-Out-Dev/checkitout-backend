package com.sm.instagram.platform.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Output DTO for UserType with translation support
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserTypeDtoOut {
    private String value;        // Original enum value: "ADMIN", "INFLUENCER", etc.
    private String label;        // Translated label: "Administrator", "Influencer", etc.
    private String originalLabel; // Fallback to enum name if translation missing
}
