package com.sm.instagram.platform.consent;

import com.sm.instagram.platform.common.base.UpdaterTracking;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
@Table(name = "consent_definition")
public class ConsentDefinition implements UpdaterTracking {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "consent_definition_generator")
    @SequenceGenerator(
            name = "consent_definition_generator",
            sequenceName = "consent_definition_seq",
            schema = "public",
            allocationSize = 50,
            initialValue = 1
    )
    private Long id;

    @NotBlank(message = "Consent type cannot be blank")
    @Size(max = 100, message = "Consent type cannot exceed 100 characters")
    @Column(name = "consent_type", unique = true, nullable = false)
    private String consentType;

    @NotBlank(message = "Name cannot be blank")
    @Size(max = 255, message = "Name cannot exceed 255 characters")
    @Column(nullable = false)
    private String name;

    @Size(max = 1000, message = "Description cannot exceed 1000 characters")
    @Column(columnDefinition = "TEXT")
    private String description;

    @Size(max = 100, message = "Regulation reference cannot exceed 100 characters")
    @Column(name = "regulation_reference")
    private String regulationReference;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;

    @OneToMany(mappedBy = "consentDefinition", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ConsentVersion> versions = new ArrayList<>();

    @Column(name = "created_at", updatable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @Size(max = 255, message = "Updater ID cannot exceed 255 characters")
    private String updaterId;

    @Override
    public void setUpdaterId(String updaterId) {
        this.updaterId = updaterId;
    }
}
