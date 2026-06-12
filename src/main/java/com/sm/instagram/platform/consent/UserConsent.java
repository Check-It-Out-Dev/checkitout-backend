package com.sm.instagram.platform.consent;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.sm.instagram.platform.common.base.UpdaterTracking;
import com.sm.instagram.platform.user.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.net.InetAddress;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "user_consent", indexes = {
        @Index(name = "idx_user_consent_lookup", columnList = "user_id, consent_version_id, created_at")
})
public class UserConsent implements UpdaterTracking {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "user_consent_generator")
    @SequenceGenerator(
            name = "user_consent_generator",
            sequenceName = "user_consent_seq",
            schema = "public",
            allocationSize = 50,
            initialValue = 1
    )
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "consent_version_id", nullable = false)
    @JsonIgnore
    private ConsentVersion consentVersion;

    @NotNull(message = "Action cannot be null")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConsentAction action;

    @NotNull(message = "Consent given cannot be null")
    @Column(name = "consent_given", nullable = false)
    private Boolean consentGiven;

    @Column(name = "ip_address")
    private InetAddress ipAddress;

    @Size(max = 1000, message = "User agent cannot exceed 1000 characters")
    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;

    @Size(max = 100, message = "Collection method cannot exceed 100 characters")
    @Column(name = "collection_method")
    private String collectionMethod = "web_form";

    @Size(max = 100, message = "Legal basis cannot exceed 100 characters")
    @Column(name = "legal_basis")
    private String legalBasis = "consent";

    @Column(name = "created_at", updatable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    @Size(max = 255, message = "Updater ID cannot exceed 255 characters")
    private String updaterId;

    @Override
    public void setUpdaterId(String updaterId) {
        this.updaterId = updaterId;
    }
}
