package com.sm.instagram.platform.support.faq.models;

import com.sm.instagram.platform.common.base.UpdaterTracking;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Entity representing a Frequently Asked Question.
 */
@Entity
@Table(name = "faq")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Faq implements UpdaterTracking {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "faq_generator")
    @SequenceGenerator(
        name = "faq_generator",
        sequenceName = "faq_seq",
        schema = "public",
        allocationSize = 50,
        initialValue = 1
    )
    private Long id;

    /**
     * The category this FAQ belongs to.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private FaqCategory category;

    /**
     * The question text.
     */
    @NotBlank(message = "Question cannot be blank")
    @Size(max = 500, message = "Question cannot exceed 500 characters")
    private String question;

    /**
     * The answer text.
     * Supports Markdown formatting.
     */
    @NotBlank(message = "Answer cannot be blank")
    @Column(columnDefinition = "TEXT")
    private String answer;

    /**
     * Order for display within the category.
     */
    private int displayOrder;

    /**
     * Whether this FAQ is currently active.
     */
    private boolean active = true;

    /**
     * When this FAQ was created.
     */
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdTime;

    /**
     * When this FAQ was last updated.
     */
    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime lastUpdateTime;

    /**
     * ID of the user who last updated this entity.
     */
    @Size(max = 255, message = "Updater ID cannot exceed 255 characters")
    private String updaterId;
}