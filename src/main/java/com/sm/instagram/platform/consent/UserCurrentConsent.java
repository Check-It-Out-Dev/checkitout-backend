package com.sm.instagram.platform.consent;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.sm.instagram.platform.user.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "user_current_consent")
@IdClass(UserCurrentConsentId.class)
public class UserCurrentConsent {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Id
    @Column(name = "consent_definition_id")
    private Long consentDefinitionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    @JsonIgnore
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "consent_definition_id", insertable = false, updatable = false)
    @JsonIgnore
    private ConsentDefinition consentDefinition;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "consent_version_id", nullable = false)
    @JsonIgnore
    private ConsentVersion consentVersion;

    @NotNull(message = "Consent given cannot be null")
    @Column(name = "consent_given", nullable = false)
    private Boolean consentGiven;

    @Column(name = "granted_at")
    private LocalDateTime grantedAt;

    @Column(name = "withdrawn_at")
    private LocalDateTime withdrawnAt;

    @NotNull(message = "Last updated cannot be null")
    @Column(name = "last_updated", nullable = false)
    private LocalDateTime lastUpdated;
}
