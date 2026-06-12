package com.sm.instagram.platform.common.util.mappers;


import org.hibernate.collection.spi.PersistentCollection;
import org.modelmapper.Conditions;
import org.modelmapper.Converter;
import org.modelmapper.ModelMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Configuration
public class ModelMapperConfig {

    private final List<MappingConfigurer> mappingConfigurers;

    public ModelMapperConfig(List<MappingConfigurer> mappingConfigurers) {
        this.mappingConfigurers = mappingConfigurers;
    }

    @Bean
    public ModelMapper modelMapper() {
        ModelMapper modelMapper = new ModelMapper();

        // Configure to skip uninitialized PersistentCollections to prevent LazyInitializationException
        modelMapper.getConfiguration()
                .setPropertyCondition(context -> 
                    !(context.getSource() instanceof PersistentCollection) || 
                    ((PersistentCollection) context.getSource()).wasInitialized()
                );

        // Custom converter for handling lazy collections
        Converter<Collection<?>, List<?>> lazyCollectionConverter = context -> {
            Collection<?> source = context.getSource();

            // Check if source is a Hibernate PersistentCollection
            if (source instanceof PersistentCollection pc) {
                if (!pc.wasInitialized()) {
                    // Return empty list for uninitialized collections
                    return new ArrayList<>();
                }
            }

            // If source is null or empty, return empty list
            if (source == null || source.isEmpty()) {
                return new ArrayList<>();
            }

            // Convert to list
            return new ArrayList<>(source);
        };

        // Add the converter for Collection to List mapping
        modelMapper.addConverter(lazyCollectionConverter);

        // Apply custom mapping configurers
        mappingConfigurers.forEach(configurer -> configurer.configureMapping(modelMapper));

        return modelMapper;
    }
}

