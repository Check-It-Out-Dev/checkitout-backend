package com.sm.instagram.platform.appliedopportunities;

import com.sm.instagram.platform.common.util.mappers.MappingConfigurer;
import com.sm.instagram.platform.common.util.mappers.UpdaterIdConverter;
import com.sm.instagram.platform.partnershipopportunities.PartnershipOpportunityConverter;
import com.sm.instagram.platform.user.UserConverter;
import org.modelmapper.ModelMapper;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

@Configuration
@Order(3)
public class AppliedOpportunityMapping implements MappingConfigurer {
    private final UpdaterIdConverter updaterIdConverter;
    private final PartnershipOpportunityConverter partnershipOpportunityConverter;
    private final UserConverter userConverter;
    private final AppliedOpportunityConverter appliedOpportunityConverter;

    public AppliedOpportunityMapping(UpdaterIdConverter updaterIdConverter,
                                     PartnershipOpportunityConverter partnershipOpportunityConverter,
                                     UserConverter userConverter,
                                     AppliedOpportunityConverter appliedOpportunityConverter) {
        this.updaterIdConverter = updaterIdConverter;
        this.partnershipOpportunityConverter = partnershipOpportunityConverter;
        this.userConverter = userConverter;
        this.appliedOpportunityConverter = appliedOpportunityConverter;
    }

    @Override
    public ModelMapper configureMapping(ModelMapper modelMapper) {
        modelMapper.typeMap(AppliedOpportunityDtoIn.class, AppliedOpportunity.class)
                .addMappings(mapper -> {
                    mapper.skip(AppliedOpportunity::setId);
                    mapper.skip(AppliedOpportunity::setLastUpdateTime);
                    mapper.skip(AppliedOpportunity::setUpdaterId);
                    mapper.skip(AppliedOpportunity::setCreatedTime);
                    mapper.skip(AppliedOpportunity::setOpportunityStatus);
                    mapper.skip(AppliedOpportunity::setRateStatus);
                    mapper.using(userConverter.toUserConverter())
                            .map(AppliedOpportunityDtoIn::getInfluencer, AppliedOpportunity::setInfluencer);
                    mapper.using(partnershipOpportunityConverter.toPartnershipOpportunityConverter())
                            .map(AppliedOpportunityDtoIn::getPartnershipOpportunity, AppliedOpportunity::setPartnershipOpportunity);
                });

        modelMapper.typeMap(AppliedOpportunity.class, AppliedOpportunityDtoOut.class)
                .addMappings(mapper -> {
                    mapper.using(updaterIdConverter.toUpdaterConverter())
                            .map(AppliedOpportunity::getUpdaterId, AppliedOpportunityDtoOut::setUpdater);
                    mapper.using(appliedOpportunityConverter.toContentDtoListConverter())
                            .map(AppliedOpportunity::getContentSubmissions, AppliedOpportunityDtoOut::setContentSubmissions);
                });
        
        modelMapper.typeMap(AppliedOpportunity.class, AppliedOpportunitySimpleDtoOut.class)
                .addMappings(mapper -> {
                    mapper.using(updaterIdConverter.toUpdaterConverter())
                            .map(AppliedOpportunity::getUpdaterId, AppliedOpportunitySimpleDtoOut::setUpdater);
                    mapper.using(appliedOpportunityConverter.toSimpleContentDtoListConverter())
                            .map(AppliedOpportunity::getContentSubmissions, AppliedOpportunitySimpleDtoOut::setContentSubmissions);
                });
        
        return modelMapper;
    }
}
