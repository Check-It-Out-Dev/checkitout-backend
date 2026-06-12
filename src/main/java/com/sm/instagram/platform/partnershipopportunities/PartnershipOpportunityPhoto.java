package com.sm.instagram.platform.partnershipopportunities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.sm.instagram.platform.common.base.UpdatableEntity;
import com.sm.instagram.platform.common.util.ValidationPatterns;
import jakarta.persistence.*;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
public class PartnershipOpportunityPhoto implements UpdatableEntity<PartnershipOpportunity> {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "partnership_opportunity_photo_generator")
    @SequenceGenerator(
        name = "partnership_opportunity_photo_generator",
        sequenceName = "partnership_opportunity_photo_seq",
        schema = "public",
        allocationSize = 50,
        initialValue = 1
    )
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "partnership_opportunity_id", nullable = false)
    @JsonIgnore  // Prevent circular reference with PartnershipOpportunity
    private PartnershipOpportunity partnershipOpportunity;
    @Pattern(
            regexp = ValidationPatterns.HTTPS_URL_PATTERN,
            message = "URL must use HTTPS protocol and be properly formatted"
    )
    @Size(max = 2048, message = "URL cannot exceed 2048 characters")
    private String url;

    private Integer orderNumber;

    private Boolean isCover;

    @Override
    public void setEntity(PartnershipOpportunity entity) {
        this.partnershipOpportunity = entity;
    }
}
