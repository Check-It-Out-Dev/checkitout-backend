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
import java.util.ArrayList;
import java.util.List;

/**
 * Entity representing a category of FAQ items.
 */
@Entity
@Table(name = "faq_category")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FaqCategory implements UpdaterTracking {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "faq_category_generator")
    @SequenceGenerator(
        name = "faq_category_generator",
        sequenceName = "faq_category_seq",
        schema = "public",
        allocationSize = 50,
        initialValue = 1
    )
    private Long id;

    /**
     * Name of this category.
     */
    @NotBlank(message = "Name cannot be blank")
    @Size(max = 100, message = "Name cannot exceed 100 characters")
    private String name;

    /**
     * Optional description of this category.
     */
    @Size(max = 500, message = "Description cannot exceed 500 characters")
    private String description;

    /**
     * Order for display in the UI.
     */
    private int displayOrder;

    /**
     * Whether this category is currently active.
     */
    private boolean active = true;

    /**
     * FAQs that belong to this category.
     */
    @OneToMany(mappedBy = "category", cascade = CascadeType.ALL)
    @OrderBy("displayOrder ASC")
    private List<Faq> faqs = new ArrayList<>();

    /**
     * When this category was created.
     */
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdTime;

    /**
     * When this category was last updated.
     */
    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime lastUpdateTime;

    /**
     * ID of the user who last updated this entity.
     */
    @Size(max = 255, message = "Updater ID cannot exceed 255 characters")
    private String updaterId;

    /**
     * Helper method to add a FAQ to this category.
     */
    public void addFaq(Faq faq) {
        faqs.add(faq);
        faq.setCategory(this);
    }
}