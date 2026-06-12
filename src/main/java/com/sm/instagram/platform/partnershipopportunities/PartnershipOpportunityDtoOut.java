package com.sm.instagram.platform.partnershipopportunities;

import com.sm.instagram.platform.address.AddressNoUserDtoOut;
import com.sm.instagram.platform.appliedopportunities.AppliedOpportunitySimpleDtoOut;
import com.sm.instagram.platform.contenttype.ContentTypeDtoOut;
import com.sm.instagram.platform.currency.CurrencyDtoOut;
import com.sm.instagram.platform.platform.PlatformDto;
import com.sm.instagram.platform.servicetype.ServiceTypeDtoOut;
import com.sm.instagram.platform.user.CompanyPublicProfileDto;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Getter
@Setter
public class PartnershipOpportunityDtoOut {
    private Long id;
    private String name;
    private String city;
    private String title;
    private String details;
    private String requirements;
    private long followersMin;
    private long followersMax;
    private CompensationTypeDtoOut compensationType;
    private int compensationAmountMin;
    private int compensationAmountMax;
    private CurrencyDtoOut currency;
    private String compensationDescription;
    private ServiceTypeDtoOut serviceType;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private boolean active;
    private CompanyPublicProfileDto company;
    private List<PartnershipOpportunityPhotoDtoOut> photos;
    private List<AppliedOpportunitySimpleDtoOut> appliedOpportunities;
    private Set<PlatformDto> platforms = new HashSet<>();
    private Set<ContentTypeDtoOut> contentTypes = new HashSet<>();
    private LocalDateTime createdTime;
    private LocalDateTime lastUpdateTime;
    private AddressNoUserDtoOut address;
    private String updater;
    private Long version;
}
