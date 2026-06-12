package com.sm.instagram.platform.notification;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Snapshot of an actor (influencer or company) at notification creation time.
 * Stored as part of NotificationSnapshot JSONB.
 *
 * Why this exists:
 * - User might change their name after notification is created
 * - User might delete their account
 * - Notification should still display original context
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActorSnapshot implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * User ID of the actor.
     * Can be used to link to profile if still exists.
     */
    private Long id;

    /**
     * Display name at notification time.
     * For influencer: firstName + lastName or Instagram handle
     * For company: company name
     */
    private String name;

    /**
     * Profile picture URL at notification time.
     * Format: /api/users/{id}/avatar or full HTTPS URL
     */
    private String avatarUrl;

    /**
     * User type for display purposes.
     * Values: "INFLUENCER" or "COMPANY"
     */
    @Schema(allowableValues = {"INFLUENCER", "COMPANY"}, description = "UserType of the actor")
    private String userType;

    /**
     * Create snapshot from User entity.
     *
     * @param user the user to snapshot
     * @return ActorSnapshot with current user data
     */
    public static ActorSnapshot fromUser(com.sm.instagram.platform.user.User user) {
        if (user == null) {
            return null;
        }

        String displayName = user.getName();
        if (displayName == null || displayName.isBlank()) {
            displayName = user.getFirstName() + " " + user.getLastName();
        }

        return ActorSnapshot.builder()
                .id(user.getId())
                .name(displayName.trim())
                .avatarUrl(user.getProfilePicture())
                .userType(user.getUserType() != null ? user.getUserType().name() : null)
                .build();
    }
}