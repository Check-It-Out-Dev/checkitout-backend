package com.sm.instagram.platform.user;

import com.sm.instagram.platform.common.util.mappers.MappingConfigurer;
import com.sm.instagram.platform.common.util.mappers.UpdaterIdConverter;
import org.modelmapper.ModelMapper;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

@Configuration
@Order(6)
public class UserMapping implements MappingConfigurer {
    private final UpdaterIdConverter updaterIdConverter;

    public UserMapping(UpdaterIdConverter updaterIdConverter) {
        this.updaterIdConverter = updaterIdConverter;
    }

    @Override
    public ModelMapper configureMapping(ModelMapper modelMapper) {
        modelMapper.typeMap(UserDtoIn.class, User.class).addMappings(mapper -> {
            mapper.skip(User::setId);
            mapper.skip(User::setUpdaterId);
            mapper.skip(User::setLastUpdateTime);
            mapper.skip(User::setCreatedTime);
            mapper.skip(User::setFirebaseUserId);
            // SECURITY (pentest 3.1): the avatar is a stored URL rendered as
            // <img src> to other users, so it is NEVER set from a full-DTO
            // create/update — only through the gated PATCH field-router
            // (UserService.handleProfilePictureUpdate, which accepts an
            // uploadId of a tracked upload owned by the caller), or by
            // OAuth/registration which set the entity directly with a trusted
            // provider URL. Skipping it here closes the "PUT /users/{id} with
            // profilePicture=attacker-host" hole without dropping an existing
            // (possibly OAuth) avatar.
            mapper.skip(User::setProfilePicture);
        });

        modelMapper.typeMap(User.class, UserDtoOut.class).addMappings(mapper ->
            mapper.using(updaterIdConverter.toUpdaterConverter()).map(User::getUpdaterId, UserDtoOut::setUpdater)
        );
        modelMapper.createTypeMap(User.class, InfluencerForCompanyProfileDto.class);
        return modelMapper;
    }
}
