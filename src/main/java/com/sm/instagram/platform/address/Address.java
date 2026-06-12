package com.sm.instagram.platform.address;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.sm.instagram.platform.common.base.UpdaterTracking;
import com.sm.instagram.platform.partnershipopportunities.PartnershipOpportunity;
import com.sm.instagram.platform.user.User;
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

/**
 * Entity representing an address in the system.
 * An address can be associated with a User and/or multiple PartnershipOpportunities.
 * Business location addresses can be shared across multiple opportunities.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
public class Address implements UpdaterTracking {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "address_generator")
    @SequenceGenerator(
        name = "address_generator",
        sequenceName = "address_seq",
        schema = "public",
        allocationSize = 50,
        initialValue = 1
    )
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    @JsonIgnore
    private User user;

    @OneToMany(mappedBy = "address", fetch = FetchType.LAZY)
    @JsonIgnore  // Prevent circular reference with PartnershipOpportunity
    private List<PartnershipOpportunity> partnershipOpportunities = new ArrayList<>();

    @NotBlank(message = "{validation.address.street.required}")
    @Size(max = 255, message = "{validation.address.street.size}")
    private String street;

    @NotBlank(message = "{validation.address.city.required}")
    @Size(max = 100, message = "{validation.address.city.size}")
    private String city;

    @NotBlank(message = "{validation.address.postalCode.required}")
    @Size(max = 20, message = "{validation.address.postalCode.size}")
    private String postalCode;

    @NotBlank(message = "{validation.address.country.required}")
    @Size(max = 100, message = "{validation.address.country.size}")
    private String country;

    @Size(max = 100, message = "{validation.address.state.size}")
    private String state;

    @Size(max = 255, message = "{validation.address.additionalInfo.size}")
    @Column(name = "additional_info")
    private String additionalInfo;

    @Column(name = "address_type")
    @NotNull(message = "{validation.address.addressType.required}")
    @NotBlank(message = "{validation.address.addressType.required}")
    @Size(max = 50, message = "{validation.address.addressType.size}")
    private String addressType = "MAIN";

    @Column(name = "is_primary")
    private boolean isPrimary = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type")
    @NotNull(message = "{validation.address.sourceType.required}")
    private AddressSourceType sourceType = AddressSourceType.CUSTOM;

    @Column(name = "is_shared")
    private boolean isShared = false;

    @Column(name = "reference_count")
    private int referenceCount = 1;

    @Column(name = "is_copied")
    private boolean isCopied = false;

    @Column(name = "source_address_id")
    private Long sourceAddressId;

    @Column(updatable = false)
    @CreationTimestamp
    private LocalDateTime createdTime;

    @UpdateTimestamp
    private LocalDateTime lastUpdateTime = LocalDateTime.now();

    @Size(max = 255, message = "{validation.updaterId.size}")
    @Pattern(regexp = "^[a-zA-Z0-9]+$", message = "{validation.updaterId.pattern}")
    private String updaterId;

    @Override
    public void setUpdaterId(String updaterId) {
        this.updaterId = updaterId;
    }

    /**
     * Validates that the address is associated with at least one entity (User or PartnershipOpportunity).
     * An address can be associated with a user, multiple partnership opportunities, or both.
     *
     * @return true if the association is valid, false otherwise
     */
    @AssertTrue(message = "{validation.address.association.required}")
    private boolean isValidAssociation() {
        // Require at least one association (allows both user and partnership opportunities)
        return user != null || (partnershipOpportunities != null && !partnershipOpportunities.isEmpty());
    }

    /**
     * Helper method to get the first partnership opportunity (for backwards compatibility).
     *
     * @return the first partnership opportunity or null if none exist
     */
    public PartnershipOpportunity getPartnershipOpportunity() {
        return partnershipOpportunities != null && !partnershipOpportunities.isEmpty()
                ? partnershipOpportunities.getFirst()
                : null;
    }

    /**
     * Helper method to set a single partnership opportunity (for backwards compatibility).
     *
     * @param opportunity the partnership opportunity to set
     */
    public void setPartnershipOpportunity(PartnershipOpportunity opportunity) {
        if (partnershipOpportunities == null) {
            partnershipOpportunities = new ArrayList<>();
        }
        partnershipOpportunities.clear();
        if (opportunity != null) {
            partnershipOpportunities.add(opportunity);
        }
    }
}