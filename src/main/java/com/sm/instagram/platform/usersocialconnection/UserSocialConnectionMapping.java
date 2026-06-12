package com.sm.instagram.platform.usersocialconnection;

import com.sm.instagram.platform.common.util.mappers.MappingConfigurer;
import com.sm.instagram.platform.platform.PlatformConverter;
import com.sm.instagram.platform.servicetype.ServiceTypeConverter;
import org.modelmapper.ModelMapper;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

@Configuration
@Order(8)
public class UserSocialConnectionMapping implements MappingConfigurer {

    private final PlatformConverter platformConverter;
    private final ServiceTypeConverter serviceTypeConverter;

    public UserSocialConnectionMapping(PlatformConverter platformConverter,
                                       ServiceTypeConverter serviceTypeConverter) {
        this.platformConverter = platformConverter;
        this.serviceTypeConverter = serviceTypeConverter;
    }

    @Override
    public ModelMapper configureMapping(ModelMapper modelMapper) {
        modelMapper.typeMap(UserSocialConnectionDtoIn.class, UserSocialConnection.class)
                .addMappings(mapper -> {
                    mapper.skip(UserSocialConnection::setId);
                    mapper.skip(UserSocialConnection::setLastUpdateTime);
                    mapper.skip(UserSocialConnection::setCreatedTime);
                    //TODO Update USER daje błąd.
                    mapper.using(platformConverter.toPlatformConverterSingle())
                            .map(UserSocialConnectionDtoIn::getPlatform, UserSocialConnection::setPlatform);
                    mapper.using(serviceTypeConverter.toServiceTypeConverter())
                            .map(UserSocialConnectionDtoIn::getServiceType, UserSocialConnection::setServiceType);
                });

        modelMapper.typeMap(UserSocialConnection.class, UserSocialConnectionDtoOut.class)
                .addMappings(mapper -> {
//                    mapper.map(src -> src.getPlatform().getId(), UserSocialConnectionDtoOut::setPlatformId);
//                    mapper.using(ctx -> enumTranslationService.translateToString((UserType) ctx.getSource()))
//                            .map(UserSocialConnection::getConnectionStatus, UserSocialConnectionDtoOut::setConnectionStatus);
                });
        return modelMapper;
    }
}
