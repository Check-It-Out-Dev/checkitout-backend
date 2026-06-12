package com.sm.instagram.platform.common.util.mappers;

import org.modelmapper.ModelMapper;

public interface MappingConfigurer {
    ModelMapper configureMapping(ModelMapper modelMapper);
}
