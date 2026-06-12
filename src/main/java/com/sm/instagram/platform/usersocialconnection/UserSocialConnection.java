package com.sm.instagram.platform.usersocialconnection;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.sm.instagram.platform.platform.Platform;
import com.sm.instagram.platform.servicetype.ServiceType;
import com.sm.instagram.platform.user.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_social_connection", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "platform_id", "social_user_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSocialConnection {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "user_social_connection_generator")
    @SequenceGenerator(
        name = "user_social_connection_generator",
        sequenceName = "user_social_connection_seq",
        schema = "public",
        allocationSize = 50,
        initialValue = 1
    )
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_user_social_connection_user"))
    @JsonIgnore
    private User user;

    @ManyToOne
    @JoinColumn(name = "platform_id", foreignKey = @ForeignKey(name = "fk_user_social_connection_platform"))
    private Platform platform;

    @Column(nullable = false)
    @Size(max = 500, message = "Social user ID cannot exceed 500 characters")
    private String socialUserId;

    @Size(max = 2048, message = "Profile URL cannot exceed 2048 characters")
    private String profileUrl;
    
    @Size(max = 2048, message = "Profile picture URL cannot exceed 2048 characters")
    private String profilePictureUrl;
    
    @Size(max = 100, message = "Display name cannot exceed 100 characters")
    private String displayName;
    
    @Size(max = 255, message = "Email cannot exceed 255 characters")
    private String email;
    
    @Size(max = 5000, message = "Note cannot exceed 5000 characters")
    private String note;

    @ManyToOne
    @JoinColumn(name = "service_id", foreignKey = @ForeignKey(name = "fk_user_social_connection_service"))
    private ServiceType serviceType;

    private Integer followersCount;

    @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    @NotNull(message = "Primary flag cannot be null")
    private Boolean isPrimary;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    @NotNull(message = "Connection status cannot be null")
    private ConnectionStatus connectionStatus;

    private LocalDateTime lastSyncTime;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdTime = LocalDateTime.now();

    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime lastUpdateTime = LocalDateTime.now();

    /**
     * Updates the profile URL based on the platform and username.
     * This method should be called whenever the platform or display name changes.
     */
    public void updateProfileUrl() {
        if (platform == null || displayName == null || displayName.isEmpty()) {
            profileUrl = null;
            return;
        }

        String platformName = platform.getName().toLowerCase();

        profileUrl = switch (platformName) {
            case "instagram" -> "https://www.instagram.com/" + displayName;
            case "tiktok" -> "https://www.tiktok.com/@" + displayName;
            case "youtube" -> "https://www.youtube.com/" + displayName;
            case "facebook" -> "https://www.facebook.com/" + displayName;
            case "twitter", "x" -> "https://twitter.com/" + displayName;
            case "linkedin" -> "https://www.linkedin.com/in/" + displayName;
            case "pinterest" -> "https://www.pinterest.com/" + displayName;
            default -> null;
        };
    }

    /**
     * Sets the display name and automatically updates the profile URL.
     *
     * @param displayName The username/display name on the social platform
     */
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
        updateProfileUrl();
    }

    /**
     * Sets the platform and automatically updates the profile URL.
     *
     * @param platform The social platform entity
     */
    public void setPlatform(Platform platform) {
        this.platform = platform;
        updateProfileUrl();
    }
}

