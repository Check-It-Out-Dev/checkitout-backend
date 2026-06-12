package com.sm.instagram.platform.userpreferences;

import com.sm.instagram.platform.common.util.mappers.EnumTranslationService;
import com.sm.instagram.platform.common.util.mappers.MappingConfigurer;
import com.sm.instagram.platform.common.util.mappers.UpdaterIdConverter;
import com.sm.instagram.platform.notification.EmailFrequency;
import org.modelmapper.ModelMapper;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

@Configuration
@Order(7)
public class UserPreferencesMapping implements MappingConfigurer {
    private final EnumTranslationService enumTranslationService;
    private final UpdaterIdConverter updaterIdConverter;

    public UserPreferencesMapping(
            EnumTranslationService enumTranslationService,
            UpdaterIdConverter updaterIdConverter
    ) {
        this.enumTranslationService = enumTranslationService;
        this.updaterIdConverter = updaterIdConverter;
    }

    @Override
    public ModelMapper configureMapping(ModelMapper modelMapper) {
        // Configure mapping for UserPreferencesDtoIn to UserPreferences
        modelMapper.typeMap(UserPreferencesDtoIn.class, UserPreferences.class)
                .addMappings(mapper -> {
                    // Skip setting these fields as they'll be set programmatically
                    mapper.skip(UserPreferences::setId);
                    mapper.skip(UserPreferences::setUpdaterId);
                    mapper.skip(UserPreferences::setLastUpdateTime);
                    mapper.skip(UserPreferences::setCreatedTime);

                    // Safely map communication frequency
                    mapper.map(src ->
                                    src.getCommunicationFrequency() != null
                                            ? EmailFrequency.valueOf(src.getCommunicationFrequency())
                                            : EmailFrequency.WEEKLY_DIGEST,
                            UserPreferences::setCommunicationFrequency);
                });

        // Configure mapping for UserPreferences to UserPreferencesDtoOut
        modelMapper.typeMap(UserPreferences.class, UserPreferencesDtoOut.class)
                .addMappings(mapper -> {
                    // Map user ID
                    mapper.map(src -> src.getUser().getId(), UserPreferencesDtoOut::setUserId);

                    // Map communication frequency to string
                    mapper.map(src ->
                                    src.getCommunicationFrequency() != null
                                            ? src.getCommunicationFrequency().name()
                                            : EmailFrequency.WEEKLY_DIGEST.name(),
                            UserPreferencesDtoOut::setCommunicationFrequency);

                    // Map updater using converter
                    mapper.using(updaterIdConverter.toUpdaterConverter())
                            .map(UserPreferences::getUpdaterId, UserPreferencesDtoOut::setUpdaterId);
                });

        return modelMapper;
    }
}