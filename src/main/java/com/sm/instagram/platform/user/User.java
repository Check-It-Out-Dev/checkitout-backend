package com.sm.instagram.platform.user;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.sm.instagram.platform.address.Address;
import com.sm.instagram.platform.common.base.UpdaterTracking;
import com.sm.instagram.platform.common.util.ValidationPatterns;
import com.sm.instagram.platform.usersocialconnection.UserSocialConnection;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "\"user\"")
public class User implements UpdaterTracking {
    // CIO-341: Removed hardcoded Polish constants. Default admin notes are now set via
    // DefaultNoteService which uses MessageSource for language-aware messages based on
    // user's X-App-Language header at account creation time.

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "user_generator")
    @SequenceGenerator(
            name = "user_generator",
            sequenceName = "user_seq",
            schema = "public",
            allocationSize = 50,
            initialValue = 1
    )
    private Long id;
    @NotBlank(message = "Firebase User ID cannot be blank")
    @Size(max = 255, message = "Firebase User ID cannot exceed 255 characters")
    @Pattern(regexp = "^[a-zA-Z0-9_\\-\\.]+$", message = "Firebase User ID must contain only letters, numbers, hyphens, underscores, and dots")
    @Column(name = "firebase_user_id", unique = true, nullable = false)
    private String firebaseUserId;
    @NotNull(message = "User Type cannot be blank")
    @Enumerated(EnumType.STRING)
    private UserType userType;
    @Column(nullable = true)
    @Email(message = "Invalid email format")
    @Size(max = 255, message = "Email cannot exceed 255 characters")
    private String email;
    @Size(min = 2, max = 50, message = "First name must be between 2 and 50 characters")
    @Column(nullable = true)  // Explicitly nullable for OAuth users
    private String firstName;
    @Size(min = 2, max = 50, message = "Last name must be between 2 and 50 characters")
    @Column(nullable = true)  // Explicitly nullable for OAuth users
    private String lastName;
    private String name;
    @Pattern(
            regexp = ValidationPatterns.HTTPS_URL_PATTERN,
            message = "URL must use HTTPS protocol and be properly formatted"
    )
    @Size(max = 2048, message = "URL cannot exceed 2048 characters")
    private String profilePicture;

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private List<Address> addresses = new ArrayList<>();

    @Pattern(regexp = "^\\+?[0-9()-]{7,25}$", message = "Invalid phone number format")
    @Size(max = 25, message = "Phone number cannot exceed 25 characters")
    private String phoneNumber;
    @Size(max = 1000, message = "Note from admin cannot exceed 1000 characters")
    private String noteFromAdmin;  // CIO-341: No default - set by DefaultNoteService at creation time
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @NotNull(message = "Account Status cannot be blank")
    private AccountStatus accountStatus = AccountStatus.IN_VALIDATION;

    /**
     * JPA version for optimistic locking.
     * Automatically incremented by JPA on each update to prevent lost updates from concurrent modifications.
     * Triggers OptimisticLockingFailureException when concurrent update detected.
     */
    @Version
    @Column(name = "version")
    private Long version;

    /**
     * Token version for immediate session invalidation.
     * Incremented when account status changes to invalidate existing JWT tokens.
     * Used by JwtAuthenticationFilter to detect stale tokens.
     */
    @Column(name = "token_version", nullable = false)
    private Long tokenVersion = 1L;

    /**
     * Increment token version to invalidate all existing sessions.
     * Should be called when account status changes (ban, activate, etc.)
     */
    public void incrementTokenVersion() {
        this.tokenVersion++;
    }

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<UserSocialConnection> socialConnections = new ArrayList<>();
    @Column(updatable = false)
    @CreationTimestamp
    private LocalDateTime createdTime;
    @UpdateTimestamp
    private LocalDateTime lastUpdateTime = LocalDateTime.now();
    private LocalDateTime deletedAt;
    @Size(max = 255, message = "Updater ID cannot exceed 255 characters")
    @Pattern(regexp = "^[a-zA-Z0-9_\\-\\.]+$", message = "Updater ID must contain only letters, numbers, hyphens, underscores, and dots")
    private String updaterId;

    // Company-specific fields
    @Size(max = 1000, message = "Company description cannot exceed 1000 characters")
    private String companyDescription;

    @Size(max = 20, message = "NIP cannot exceed 20 characters")
    private String nip;

    @Column(nullable = false)
    private Boolean premium = false;

    // Email verification tracking (synced from Firebase)
    @Column(name = "email_verified", nullable = false)
    private Boolean emailVerified = false;

    @Column(name = "email_verification_sent_at")
    private LocalDateTime emailVerificationSentAt;

    @Column(name = "email_verified_at")
    private LocalDateTime emailVerifiedAt;

    // Step-up codes are always sent to this address (last proven email).
    // Updated only when email verification succeeds. Stays unchanged on email change.
    @Column(name = "last_verified_email")
    private String lastVerifiedEmail;

    // HIGH-001 FIX: Track password reset requests to prevent spam
    @Column(name = "password_reset_sent_at")
    private LocalDateTime passwordResetSentAt;

    /**
     * Whether the user has accepted the latest version of all required legal documents.
     * Set to false by migration when new document versions are introduced.
     * Set to true when user accepts all current documents.
     */
    @Column(name = "newest_consents_accepted", nullable = false)
    private Boolean newestConsentsAccepted = false;

    /**
     * Whether the user has completed initial account setup (email verified + profile complete).
     * PostgreSQL is the source of truth for this flag.
     * If false and Firebase email_verified=true, Firebase gets reset to false (PG wins).
     */
    @Column(name = "initial_account_setup_completed", nullable = false)
    private Boolean initialAccountSetupCompleted = false;

    @Override
    public void setUpdaterId(String updaterId) {
        this.updaterId = updaterId;
    }
}
