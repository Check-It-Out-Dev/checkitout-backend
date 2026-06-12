package com.sm.instagram.platform.support.faq.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO for incoming FAQ category creation requests.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FaqCategoryDtoIn {

    /**
     * Name of the category.
     */
    @NotBlank(message = "{validation.faqCategory.name.required}")
    @Size(max = 100, message = "{validation.faqCategory.name.size}")
    private String name;

    /**
     * Optional description of the category.
     */
    @Size(max = 500, message = "{validation.faqCategory.description.size}")
    private String description;

    /**
     * Order for display in the UI.
     */
    private int displayOrder;

    /**
     * Whether this category is currently active.
     */
    private boolean active = true;
}