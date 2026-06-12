package com.sm.instagram.platform.user;

import com.sm.instagram.platform.common.util.mappers.MappingConfigurer;
import com.sm.instagram.platform.usersocialconnection.UserSocialConnection;
import org.modelmapper.ModelMapper;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

@Configuration
@Order(5)
public class PublicProfileMapping implements MappingConfigurer {

    @Override
    public ModelMapper configureMapping(ModelMapper modelMapper) {

        // InfluencerPublicProfileDto mapping
        modelMapper.createTypeMap(UserSocialConnection.class, InfluencerPublicProfileDto.class)
                .addMappings(mapper -> {
                    mapper.map(src -> src.getUser().getName(), InfluencerPublicProfileDto::setName);
                    mapper.map(src -> src.getUser().getProfilePicture(), InfluencerPublicProfileDto::setProfilePicture);
                    mapper.map(src -> src.getPlatform().getName(), InfluencerPublicProfileDto::setPlatformName);
                    mapper.map(UserSocialConnection::getDisplayName, InfluencerPublicProfileDto::setDisplayName);
                    mapper.map(UserSocialConnection::getProfileUrl, InfluencerPublicProfileDto::setProfileUrl);
                    mapper.map(UserSocialConnection::getFollowersCount, InfluencerPublicProfileDto::setFollowersCount);
                });

        // Create empty type map first, then add mappings, then implicitMappings
        modelMapper.createTypeMap(User.class, CompanyPublicProfileDto.class);
        
        modelMapper.typeMap(User.class, CompanyPublicProfileDto.class)
                .addMappings(mapper -> {
                    mapper.map(User::getId, CompanyPublicProfileDto::setId);
                    mapper.map(User::getName, CompanyPublicProfileDto::setName);
                    mapper.map(User::getProfilePicture, CompanyPublicProfileDto::setProfilePicture);
                    mapper.map(User::getAddresses, CompanyPublicProfileDto::setAddresses);
                    // Optional fields like website or description can be set manually or with post-processing
                })
                .implicitMappings();

        return modelMapper;
    }
}