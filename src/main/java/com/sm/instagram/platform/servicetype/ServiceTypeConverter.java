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
        // Null-safe like AddressMapping's converters: an absent optional FK
        // in the dto maps to null instead of findById(null) exploding the
        // whole request into a MappingException.
        return ctx -> ctx.getSource() == null
                ? null
                : serviceTypeRepository.findById(ctx.getSource())
                        .orElseThrow(() -> new IllegalArgumentException("ServiceType not found: " + ctx.getSource()));
    }

}
