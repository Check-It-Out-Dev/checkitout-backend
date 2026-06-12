package com.sm.instagram.platform.support.faq.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO for incoming FAQ creation requests.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FaqDtoIn {

    /**
     * The question text.
     */
    @NotBlank(message = "{validation.faq.question.required}")
    @Size(max = 500, message = "{validation.faq.question.size}")
    private String question;

    /**
     * The answer text.
     * Supports Markdown formatting.
     */
    @NotBlank(message = "{validation.faq.answer.required}")
    @Size(max = 10000, message = "{validation.faq.answer.size}")
    private String answer;

    /**
     * ID of the category this FAQ belongs to.
     */
    private Long categoryId;

    /**
     * Order for display within the category.
     */
    private int displayOrder;

    /**
     * Whether this FAQ is currently active.
     */
    private boolean active = true;
}