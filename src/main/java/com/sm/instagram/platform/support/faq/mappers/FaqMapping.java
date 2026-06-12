package com.sm.instagram.platform.support.faq.mappers;

import com.sm.instagram.platform.common.util.mappers.MappingConfigurer;
import com.sm.instagram.platform.common.util.mappers.UpdaterIdConverter;
import com.sm.instagram.platform.support.faq.dtos.FaqCategoryDtoOut;
import com.sm.instagram.platform.support.faq.dtos.FaqDtoIn;
import com.sm.instagram.platform.support.faq.dtos.FaqDtoOut;
import com.sm.instagram.platform.support.faq.models.Faq;
import com.sm.instagram.platform.support.faq.models.FaqCategory;
import com.sm.instagram.platform.support.faq.repositories.FaqCategoryRepository;
import org.modelmapper.ModelMapper;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

/**
 * Configures mappings for FAQ and FaqCategory entities.
 */
@Configuration
@Order(4)
public class FaqMapping implements MappingConfigurer {

    private final UpdaterIdConverter updaterIdConverter;
    private final FaqCategoryRepository faqCategoryRepository;

    public FaqMapping(UpdaterIdConverter updaterIdConverter, FaqCategoryRepository faqCategoryRepository) {
        this.updaterIdConverter = updaterIdConverter;
        this.faqCategoryRepository = faqCategoryRepository;
    }

    @Override
    public ModelMapper configureMapping(ModelMapper modelMapper) {
        // Configure FAQ to FaqDtoOut mapping
        modelMapper.createTypeMap(Faq.class, FaqDtoOut.class)
                .addMappings(mapper -> {
                    // Map category properties
                    mapper.map(src -> src.getCategory().getId(), FaqDtoOut::setCategoryId);
                    mapper.map(src -> src.getCategory().getName(), FaqDtoOut::setCategoryName);

                    // Map updater info using converter
                    mapper.using(updaterIdConverter.toUpdaterConverter())
                            .map(Faq::getUpdaterId, FaqDtoOut::setUpdaterId);
                });

        // Configure FaqDtoIn to Faq mapping - using empty type map to avoid conflicts
        modelMapper.emptyTypeMap(FaqDtoIn.class, Faq.class)
                .addMappings(mapper -> {
                    mapper.map(FaqDtoIn::getQuestion, Faq::setQuestion);
                    mapper.map(FaqDtoIn::getAnswer, Faq::setAnswer);
                    mapper.map(FaqDtoIn::getDisplayOrder, Faq::setDisplayOrder);
                    mapper.map(FaqDtoIn::isActive, Faq::setActive);
                });

        // Configure FaqCategory to FaqCategoryDtoOut mapping
        modelMapper.createTypeMap(FaqCategory.class, FaqCategoryDtoOut.class)
                .addMappings(mapper -> {
                    // Map updater info using converter
                    mapper.using(updaterIdConverter.toUpdaterConverter())
                            .map(FaqCategory::getUpdaterId, FaqCategoryDtoOut::setUpdaterId);
                });

        return modelMapper;
    }
}