package com.sm.instagram.platform.contenttype;

import org.modelmapper.Converter;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class ContentTypeConverter {
    private final ContentTypeRepository contentTypeRepository;

    public ContentTypeConverter(ContentTypeRepository contentTypeRepository) {
        this.contentTypeRepository = contentTypeRepository;
    }

    public Converter<Set<Long>, Set<ContentType>> toContentTypeConverter() {
        return ctx -> {
            Set<Long> sourceSet = ctx.getSource();
            if (sourceSet == null) {
                return new HashSet<>();
            }

            Set<Long> invalidIds = sourceSet.stream()
                    .filter(id -> !contentTypeRepository.existsById(id))
                    .collect(Collectors.toSet());

            if (!invalidIds.isEmpty()) {
                throw new IllegalArgumentException("Content Types not found: " + invalidIds);
            }

            return sourceSet.stream()
                    .map(id -> contentTypeRepository.findById(id)
                            .orElseThrow(() -> new IllegalArgumentException("Content Type not found: " + id)))
                    .collect(Collectors.toSet());
        };
    }


}
