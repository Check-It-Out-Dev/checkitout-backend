package com.sm.instagram.platform.support.faq.dtos;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * DTO for outgoing FAQ data.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FaqDtoOut {

    /**
     * Unique ID of the FAQ.
     */
    private Long id;

    /**
     * The question text.
     */
    private String question;

    /**
     * The answer text.
     * Formatted as Markdown.
     */
    private String answer;

    /**
     * ID of the category this FAQ belongs to.
     */
    private Long categoryId;

    /**
     * Name of the category this FAQ belongs to.
     */
    private String categoryName;

    /**
     * Order for display within the category.
     */
    private int displayOrder;

    /**
     * Whether this FAQ is currently active.
     */
    private boolean active;

    /**
     * When this FAQ was created.
     */
    private LocalDateTime createdTime;

    /**
     * When this FAQ was last updated.
     */
    private LocalDateTime lastUpdateTime;

    /**
     * ID of the user who last updated this entity.
     */
    private String updaterId;
}