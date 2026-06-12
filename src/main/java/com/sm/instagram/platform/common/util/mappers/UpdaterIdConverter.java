package com.sm.instagram.platform.common.util.mappers;

import org.modelmapper.Converter;
import org.modelmapper.spi.MappingContext;
import org.springframework.stereotype.Component;

@Component
public class UpdaterIdConverter {

    public Converter<String, String> toUpdaterConverter() {
        return MappingContext::getSource;
    }
}
