package com.sm.instagram.platform.servicetype;

import org.modelmapper.Converter;
import org.springframework.stereotype.Component;

@Component
public class ServiceTypeConverter {
    private final ServiceTypeRepository serviceTypeRepository;

    public ServiceTypeConverter(ServiceTypeRepository serviceTypeRepository) {
        this.serviceTypeRepository = serviceTypeRepository;
    }

    public Converter<Long, ServiceType> toServiceTypeConverter() {
        return ctx -> serviceTypeRepository.findById(ctx.getSource())
                .orElseThrow(() -> new IllegalArgumentException("ServiceType not found: " + ctx.getSource()));
    }

}
