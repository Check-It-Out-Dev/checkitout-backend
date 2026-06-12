package com.sm.instagram.platform.consent;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.sm.instagram.platform.common.base.UpdaterTracking;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "consent_version", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"consent_definition_id", "version"})
})
public class ConsentVersion implements UpdaterTracking {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "consent_version_generator")
    @SequenceGenerator(
            name = "consent_version_generator",
            sequenceName = "consent_version_seq",
            schema = "public",
            allocationSize = 50,
            initialValue = 1
    )
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "consent_definition_id", nullable = false)
    @JsonIgnore
    private ConsentDefinition consentDefinition;

    @NotBlank(message = "Version cannot be blank")
    @Size(max = 50, message = "Version cannot exceed 50 characters")
    @Column(nullable = false)
    private String version;

    @NotBlank(message = "Consent text cannot be blank")
    @Column(name = "consent_text", nullable = false, columnDefinition = "TEXT")
    private String consentText;

    @Size(max = 500, message = "Policy URL cannot exceed 500 characters")
    @Column(name = "policy_url")
    private String policyUrl;

    @NotNull(message = "Effective from date cannot be null")
    @Column(name = "effective_from", nullable = false)
    private LocalDateTime effectiveFrom;

    @Column(name = "effective_until")
    private LocalDateTime effectiveUntil;

    @OneToMany(mappedBy = "consentVersion", cascade = CascadeType.ALL)
    private List<UserConsent> userConsents = new ArrayList<>();

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
