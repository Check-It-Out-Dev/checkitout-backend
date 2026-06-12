package com.sm.instagram.platform.partnershipopportunities;

import com.sm.instagram.platform.address.AddressDtoIn;
import com.sm.instagram.platform.common.validation.FutureOrPresentDate;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Getter
@Setter
public class PartnershipOpportunityDtoIn {
    private static final int MAX_COMPENSATION_AMOUNT = 1_000_000;
    private Long id;
    @NotBlank(message = "{validation.partnership.name.required}")
    @Size(max = 255, message = "{validation.partnership.name.size}")
    private String name;
    @NotBlank(message = "{validation.partnership.city.required}")
    private String city;
    @NotBlank(message = "{validation.partnership.title.required}")
    @Size(max = 255, message = "{validation.partnership.title.size}")
    private String title;
    @Size(max = 2000, message = "{validation.partnership.details.size}")
    private String details;
    @Size(max = 2000, message = "{validation.partnership.requirements.size}")
    private String requirements;
    private AddressDtoIn address;
    private Long addressId;
    private CompensationType compensationType;
    @Min(value = 0, message = "{validation.partnership.compensationMin.min}")
    @Max(value = MAX_COMPENSATION_AMOUNT, message = "Minimum compensation amount must not exceed " + MAX_COMPENSATION_AMOUNT)
    private int compensationAmountMin;
    @Min(value = 0, message = "{validation.partnership.compensationMax.min}")
    @Max(value = MAX_COMPENSATION_AMOUNT, message = "Maximum compensation amount must not exceed " + MAX_COMPENSATION_AMOUNT)
    private int compensationAmountMax;
    private Long currency;
    @Size(max = 500, message = "{validation.partnership.compensationDescription.size}")
    private String compensationDescription;
    @Min(value = 0, message = "{validation.partnership.followersMin.min}")
    private long followersMin;
    @Min(value = 0, message = "{validation.partnership.followersMax.min}")
    private long followersMax;
    private Set<Long> platforms;
    private Set<Long> contentTypes;
    @NotNull(message = "{validation.partnership.company.required}")
    private Long company;
    private Long serviceType;
    private boolean active;
    @Size(max = 6, message = "{validation.photos.max.constraint}")
    @Valid
    private List<PartnershipOpportunityPhotoDtoIn> photos = new ArrayList<>();
    @FutureOrPresentDate(message = "{validation.partnershipOpportunity.startDate}")
    private LocalDateTime startDate;
    @FutureOrPresentDate(message = "{validation.partnershipOpportunity.endDate}")
    private LocalDateTime endDate;
    private LocalDateTime createdTime;
    private LocalDateTime lastUpdateTime;
    private Long version;
}
