package com.sm.instagram.platform.platform;

import org.modelmapper.Converter;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class PlatformConverter {
    private final PlatformRepository platformRepository;

    public PlatformConverter(PlatformRepository platformRepository) {
        this.platformRepository = platformRepository;
    }

    public Converter<Set<Long>, Set<Platform>> toPlatformConverter() {
        return ctx -> {
            Set<Long> sourceSet = ctx.getSource();
            if (sourceSet == null) {
                return new HashSet<>();
            }
            Set<Long> invalidIds = sourceSet.stream()
                    .filter(id -> !platformRepository.existsById(id))
                    .collect(Collectors.toSet());
            if (!invalidIds.isEmpty()) {
                throw new IllegalArgumentException("Platforms not found: " + invalidIds);
            }
            return sourceSet.stream()
                    .map(id -> platformRepository.findById(id)
                            .orElseThrow(() -> new IllegalArgumentException("Platform not found for id: " + id)))
                    .collect(Collectors.toSet());
        };
    }
    public Converter<Long, Platform> toPlatformConverterSingle() {
        return ctx -> {
            Long id = ctx.getSource();
            if (id == null) return null;
            return platformRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Platform not found for id: " + id));
        };
    }


}
