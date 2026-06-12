package com.sm.instagram.platform.servicetype;

import com.sm.instagram.platform.common.util.mappers.MappingConfigurer;
import org.modelmapper.ModelMapper;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ServiceTypeMapper implements MappingConfigurer {
    @Override
    public ModelMapper configureMapping(ModelMapper modelMapper) {
        modelMapper.createTypeMap(ServiceTypeDto.class, ServiceType.class)
                .addMappings(mapper -> mapper.skip(ServiceType::setId));

        return modelMapper;
    }
}
