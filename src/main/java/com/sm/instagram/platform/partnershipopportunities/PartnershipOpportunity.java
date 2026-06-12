package com.sm.instagram.platform.partnershipopportunities;

import com.sm.instagram.platform.address.Address;
import com.sm.instagram.platform.appliedopportunities.AppliedOpportunity;
import com.sm.instagram.platform.city.City;
import com.sm.instagram.platform.common.base.UpdaterTracking;
import com.sm.instagram.platform.contenttype.ContentType;
import com.sm.instagram.platform.currency.Currency;
import com.sm.instagram.platform.platform.Platform;
import com.sm.instagram.platform.servicetype.ServiceType;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
public class PartnershipOpportunity implements UpdaterTracking {
    private static final int MAX_COMPENSATION_AMOUNT = 1_000_000;
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "partnership_opportunity_generator")
    @SequenceGenerator(
        name = "partnership_opportunity_generator",
        sequenceName = "partnership_opportunity_seq",
        schema = "public",
        allocationSize = 50,
        initialValue = 1
    )
    private Long id;
    @NotBlank(message = "Name cannot be blank")
    @Size(max = 255, message = "Name cannot exceed 255 characters")
    private String name;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "city_id", nullable = false)
    @NotNull(message = "City cannot be blank")
    private City city;
    @ManyToOne(fetch = FetchType.LAZY, cascade = CascadeType.PERSIST)
    @JoinColumn(name = "address_id")
    @NotNull(message = "Address cannot be blank")
    private Address address;

    @NotBlank(message = "Title cannot be blank")
    @Size(max = 255, message = "Title cannot exceed 255 characters")
    private String title;
    @Size(max = 2000, message = "Details cannot exceed 2000 characters")
    private String details;
    @Size(max = 2000, message = "Requirements cannot exceed 2000 characters")
    private String requirements;
    @Enumerated(EnumType.STRING)
    @NotNull(message = "Compensation type must not be null")
    private CompensationType compensationType;
    @ManyToOne
    @JoinColumn(name = "currency_id")
    private Currency currency;
    @Min(value = 0, message = "Minimum compensation amount must be non-negative")
    @Max(value = MAX_COMPENSATION_AMOUNT, message = "Minimum compensation amount must not exceed " + MAX_COMPENSATION_AMOUNT)
    // Boxed: the DB column (compensation_amount_min INTEGER) is nullable; a primitive cannot
    // hydrate a legacy NULL row and would crash Hibernate with a JpaSystemException -> HTTP 400.
    private Integer compensationAmountMin;
    @Min(value = 0, message = "Maximum compensation amount must be non-negative")
    @Max(value = MAX_COMPENSATION_AMOUNT, message = "Maximum compensation amount must not exceed " + MAX_COMPENSATION_AMOUNT)
    // Boxed: see compensationAmountMin — nullable column, legacy NULL rows must hydrate.
    private Integer compensationAmountMax;
    @Min(value = 0, message = "Minimum followers amount must be non-negative")
    private long followersMin;
    @Min(value = 0, message = "Maximum followers amount must be non-negative")
    private long followersMax;
    @Size(max = 500, message = "Compensation description cannot exceed 500 characters")
    private String compensationDescription;
    @ManyToMany(fetch = FetchType.LAZY, cascade = {CascadeType.MERGE})
    @JoinTable(
            name = "partnership_opportunity_platform",
            joinColumns = @JoinColumn(name = "partnership_opportunity_id"),
            inverseJoinColumns = @JoinColumn(name = "platform_id"),
            indexes = {
                    @Index(columnList = "partnership_opportunity_id"),
                    @Index(columnList = "platform_id")
            }
    )
    private Set<Platform> platforms = new HashSet<>();
    @ManyToMany(fetch = FetchType.LAZY, cascade = {CascadeType.MERGE})
    @JoinTable(
            name = "partnership_opportunity_content_type",
            joinColumns = @JoinColumn(name = "partnership_opportunity_id"),
            inverseJoinColumns = @JoinColumn(name = "content_type_id"),
            indexes = {
                    @Index(columnList = "partnership_opportunity_id"),
                    @Index(columnList = "content_type_id")
            }
    )
    private Set<ContentType> contentTypes = new HashSet<>();
    // Note: Date validation is handled at DTO level (PartnershipOpportunityDtoIn)
    // Entity-level validation was removed to allow soft-delete of opportunities with past dates
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    @OneToMany(mappedBy = "partnershipOpportunity", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PartnershipOpportunityPhoto> photos = new ArrayList<>();

    @OneToMany(mappedBy = "partnershipOpportunity", fetch = FetchType.LAZY)
    private List<AppliedOpportunity> appliedOpportunities = new ArrayList<>();
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    @NotNull(message = "Company cannot be null")
    private User company;
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "service_id")
    private ServiceType serviceType;
    /**
     * JPA version for optimistic locking.
     * Prevents lost updates when multiple users modify opportunity concurrently.
     */
    @Version
    @Column(name = "version")
    private Long version;

    @Column(updatable = false)
    @CreationTimestamp
    private LocalDateTime createdTime;
    @UpdateTimestamp
    private LocalDateTime lastUpdateTime = LocalDateTime.now();
    @Size(max = 255, message = "Firebase User ID cannot exceed 255 characters")
    @Pattern(regexp = "^[a-zA-Z0-9._-]+$", message = "Updater ID must contain only letters, numbers, dots, hyphens or underscores")
    private String updaterId;

    /**
     * Indicates whether this opportunity is active or has been soft-deleted.
     * When false, the opportunity should be excluded from normal queries.
     */
    private boolean active = true;

    public void setPhotos(List<PartnershipOpportunityPhoto> photos) {
        this.photos.clear();
        if (photos != null) {
            this.photos.addAll(photos);
            for (PartnershipOpportunityPhoto photo : photos) {
                photo.setPartnershipOpportunity(this);
            }
        }
    }

    @AssertTrue(message = "End date must be after start date")
    public boolean isEndDateNotBeforeStartDate() {
        return endDate == null || startDate == null || !endDate.isBefore(startDate);
    }

    @AssertTrue(message = "Maximum compensation amount must be greater than or equal to minimum amount and not bigger than " + MAX_COMPENSATION_AMOUNT)
    public boolean isValidCompensationRange() {
        // Legacy rows may carry NULL compensation (columns are nullable); a partial/absent range
        // cannot be validated, so don't block hydration or re-save (e.g. soft-delete) of such rows.
        if (compensationAmountMin == null || compensationAmountMax == null) {
            return true;
        }
        return compensationAmountMax >= compensationAmountMin
                && compensationAmountMax <= MAX_COMPENSATION_AMOUNT;
    }

    @AssertTrue(message = "Maximum followers amount must be greater than or equal to minimum amount")
    public boolean isValidFollowersRange() {
        return followersMax >= followersMin;
    }
}
