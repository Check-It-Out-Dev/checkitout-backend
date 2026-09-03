package com.sm.instagram.platform.partnershipopportunities;

import com.sm.instagram.platform.address.AddressRepository;
import com.sm.instagram.platform.city.CityRepository;
import com.sm.instagram.platform.common.exceptions.ResourceNotFoundException;
import com.sm.instagram.platform.common.util.mappers.MappingConfigurer;
import com.sm.instagram.platform.contenttype.ContentTypeConverter;
import com.sm.instagram.platform.currency.CurrencyConverter;
import com.sm.instagram.platform.platform.PlatformConverter;
import com.sm.instagram.platform.servicetype.ServiceTypeConverter;
import com.sm.instagram.platform.user.UserConverter;
import org.modelmapper.ModelMapper;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

@Configuration
@Order(1)
public class PartnershipOpportunityMapping implements MappingConfigurer {
    private final AddressRepository addressRepository;
    private final CityRepository cityRepository;
    private final ServiceTypeConverter serviceTypeConverter;
    private final UserConverter userConverter;
    private final PlatformConverter platformConverter;
    private final ContentTypeConverter contentTypeConverter;
    private final CurrencyConverter currencyConverter;

    public PartnershipOpportunityMapping(AddressRepository addressRepository,
                                         CityRepository cityRepository,
                                         ServiceTypeConverter serviceTypeConverter,
                                         UserConverter userConverter,
                                         PlatformConverter platformConverter,
                                         ContentTypeConverter contentTypeConverter,
                                         CurrencyConverter currencyConverter) {
        this.addressRepository = addressRepository;
        this.cityRepository = cityRepository;
        this.serviceTypeConverter = serviceTypeConverter;
        this.userConverter = userConverter;
        this.platformConverter = platformConverter;
        this.contentTypeConverter = contentTypeConverter;
        this.currencyConverter = currencyConverter;
    }

    @Override
    public ModelMapper configureMapping(ModelMapper modelMapper) {
        // Use emptyTypeMap to disable ALL implicit matching - prevents ambiguity
        // Must explicitly map or skip EVERY field
        modelMapper.emptyTypeMap(PartnershipOpportunityDtoIn.class, PartnershipOpportunity.class)
                .addMappings(mapper -> {
                    // ═══════════════════════════════════════════════════════════
                    // SKIP: Auto-managed fields (JPA, Hibernate, UpdaterTracking)
                    // ═══════════════════════════════════════════════════════════
                    mapper.skip(PartnershipOpportunity::setId);
                    mapper.skip(PartnershipOpportunity::setCreatedTime);
                    mapper.skip(PartnershipOpportunity::setLastUpdateTime);
                    mapper.skip(PartnershipOpportunity::setUpdaterId);
                    mapper.skip(PartnershipOpportunity::setPhotos); // Handled separately in service
                    mapper.skip(PartnershipOpportunity::setAppliedOpportunities); // Managed by JPA

                    // ═══════════════════════════════════════════════════════════
                    // EXPLICIT: Simple field mappings (String, primitives, enums)
                    // ═══════════════════════════════════════════════════════════
                    mapper.map(PartnershipOpportunityDtoIn::getName, PartnershipOpportunity::setName);
                    mapper.map(PartnershipOpportunityDtoIn::getTitle, PartnershipOpportunity::setTitle);
                    mapper.map(PartnershipOpportunityDtoIn::getDetails, PartnershipOpportunity::setDetails);
                    mapper.map(PartnershipOpportunityDtoIn::getRequirements, PartnershipOpportunity::setRequirements);
                    mapper.map(PartnershipOpportunityDtoIn::getCompensationType, PartnershipOpportunity::setCompensationType);
                    mapper.map(PartnershipOpportunityDtoIn::getCompensationAmountMin, PartnershipOpportunity::setCompensationAmountMin);
                    mapper.map(PartnershipOpportunityDtoIn::getCompensationAmountMax, PartnershipOpportunity::setCompensationAmountMax);
                    mapper.map(PartnershipOpportunityDtoIn::getCompensationDescription, PartnershipOpportunity::setCompensationDescription);
                    mapper.map(PartnershipOpportunityDtoIn::getFollowersMin, PartnershipOpportunity::setFollowersMin);
                    mapper.map(PartnershipOpportunityDtoIn::getFollowersMax, PartnershipOpportunity::setFollowersMax);
                    mapper.map(PartnershipOpportunityDtoIn::getStartDate, PartnershipOpportunity::setStartDate);
                    mapper.map(PartnershipOpportunityDtoIn::getEndDate, PartnershipOpportunity::setEndDate);
                    mapper.map(PartnershipOpportunityDtoIn::isActive, PartnershipOpportunity::setActive);

                    // ═══════════════════════════════════════════════════════════
                    // CUSTOM CONVERTERS: Complex entity relationships
                    // ═══════════════════════════════════════════════════════════

                    // Address: addressId (Long) → Address entity
                    // This resolves the ambiguity by explicitly using addressId, ignoring nested address DTO
                    mapper.using(ctx -> {
                        Long addressId = (Long) ctx.getSource();
                        if (addressId == null) {
                            return null;
                        }
                        return addressRepository.findById(addressId)
                                .orElseThrow(() -> new ResourceNotFoundException("error.business.item_not_found", addressId));
                    }).map(PartnershipOpportunityDtoIn::getAddressId, PartnershipOpportunity::setAddress);

                    // Note: The nested address DTO field (AddressDtoIn) is intentionally NOT mapped.
                    // We use addressId (Long) for entity resolution via repository lookup above.
                    // The AddressMapping.java now explicitly skips Address::setId to prevent ambiguity.

                    // City: cityName (String) → City entity
                    mapper.using(ctx -> {
                        String cityName = (String) ctx.getSource();
                        if (cityName == null || cityName.trim().isEmpty()) {
                            throw new IllegalArgumentException("City must not be null or empty.");
                        }
                        return cityRepository.findByName(cityName)
                                .orElseThrow(() -> new ResourceNotFoundException("error.business.item_not_found", cityName));
                    }).map(PartnershipOpportunityDtoIn::getCity, PartnershipOpportunity::setCity);

                    // Company: companyId (Long) → User entity
                    mapper.using(userConverter.toUserConverter())
                            .map(PartnershipOpportunityDtoIn::getCompany, PartnershipOpportunity::setCompany);

                    // Currency: currencyId (Long) → Currency entity
                    mapper.using(currencyConverter.toCurrencyConverter())
                            .map(PartnershipOpportunityDtoIn::getCurrency, PartnershipOpportunity::setCurrency);

                    // ServiceType: serviceTypeId (Long) → ServiceType entity
                    mapper.using(serviceTypeConverter.toServiceTypeConverter())
                            .map(PartnershipOpportunityDtoIn::getServiceType, PartnershipOpportunity::setServiceType);

                    // Platforms: Set<Long> → Set<Platform>
                    mapper.using(platformConverter.toPlatformConverter())
                            .map(PartnershipOpportunityDtoIn::getPlatforms, PartnershipOpportunity::setPlatforms);

                    // ContentTypes: Set<Long> → Set<ContentType>
                    mapper.using(contentTypeConverter.toContentTypeConverter())
                            .map(PartnershipOpportunityDtoIn::getContentTypes, PartnershipOpportunity::setContentTypes);
                });

        // Photo DTO → entity: explicit empty type map for the same reason as
        // above. Without it, implicit STANDARD matching maps PhotoDtoIn.id onto
        // the destination path partnershipOpportunity.id (the source class name
        // PartnershipOpportunity*Photo*DtoIn supplies the parent tokens), which
        // corrupts the attached parent's identifier when mapping onto a managed
        // photo. Copy content fields only; id is DB-generated and the parent
        // association is set explicitly by every call site.
        modelMapper.emptyTypeMap(PartnershipOpportunityPhotoDtoIn.class, PartnershipOpportunityPhoto.class)
                .addMappings(mapper -> {
                    mapper.skip(PartnershipOpportunityPhoto::setId);
                    mapper.skip(PartnershipOpportunityPhoto::setPartnershipOpportunity);
                    // url is BE-derived from the tracked uploadId in the
                    // service (pentest 3.1) — never mapped from the DTO.
                    mapper.skip(PartnershipOpportunityPhoto::setUrl);
                    mapper.map(PartnershipOpportunityPhotoDtoIn::getOrderNumber, PartnershipOpportunityPhoto::setOrderNumber);
                    mapper.map(PartnershipOpportunityPhotoDtoIn::getIsCover, PartnershipOpportunityPhoto::setIsCover);
                });

        // Note: PartnershipOpportunity → PartnershipOpportunityDtoOut mapping handled manually in service
        // This avoids ModelMapper configuration issues with translation DTOs

        return modelMapper;
    }
}
