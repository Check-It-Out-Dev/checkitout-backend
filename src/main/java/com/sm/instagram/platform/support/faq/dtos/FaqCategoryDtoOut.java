package com.sm.instagram.platform.support.faq.dtos;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * DTO for outgoing FAQ category data.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FaqCategoryDtoOut {

    /**
     * Unique ID of the category.
     */
    private Long id;

    /**
     * Name of the category.
     */
    private String name;

    /**
     * Optional description of the category.
     */
    private String description;

    /**
     * Order for display in the UI.
     */
    private int displayOrder;

    /**
     * Whether this category is currently active.
     */
    private boolean active;

    /**
     * When this category was created.
     */
    private LocalDateTime createdTime;

    /**
     * When this category was last updated.
     */
    private LocalDateTime lastUpdateTime;

    /**
     * ID of the user who last updated this entity.
     */
    private String updaterId;

    /**
     * FAQs that belong to this category.
     */
    private List<FaqDtoOut> faqs = new ArrayList<>();
}