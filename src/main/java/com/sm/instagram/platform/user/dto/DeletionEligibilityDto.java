package com.sm.instagram.platform.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO representing the deletion eligibility status for a user.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeletionEligibilityDto {
    private Long userId;
    private String firebaseUserId;
    private String userEmail;
    @Schema(allowableValues = {"ADMIN", "PENDING_ADMIN", "INFLUENCER", "COMPANY"}, description = "UserType enum name")
    private String userType;
    private boolean canSoftDelete;
    private boolean canPermanentDelete;
    private List<DeletionBlocker> softDeleteBlockers;
    private List<DeletionBlocker> permanentDeleteBlockers;
    private String summary;
}
