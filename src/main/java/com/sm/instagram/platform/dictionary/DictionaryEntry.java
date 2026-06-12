package com.sm.instagram.platform.dictionary;

import com.sm.instagram.platform.common.base.UpdaterTracking;
import jakarta.persistence.*;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Represents a dictionary entry for storing translations or key-value pairs.
 *
 * This entity supports multilingual and categorized dictionary entries with
 * automatic timestamp tracking.
 *
 * @author Peter Żmudzki
 * @version 1.0
 * @since 2024-03-24
 */
@Getter
@Setter
@Entity
@Table(name = "dictionary_entries")
@NoArgsConstructor
@AllArgsConstructor
public class DictionaryEntry implements UpdaterTracking {
    /**
     * Unique identifier for the dictionary entry.
     * Generated automatically as a UUID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * The key or identifier for the dictionary entry.
     * Cannot be null.
     */

    @Setter
    @Getter
    @Column(name = "entry_key", nullable = false)
    private String key;

    /**
     * The value or translation associated with the key.
     * Cannot be null and has a maximum length of 1000 characters.
     */

    @Setter
    @Getter
    @Column(name = "value", nullable = false, length = 1000)
    private String value;

    /**
     * Language code for the entry (e.g., 'en', 'es', 'fr').
     * Cannot be null and limited to 10 characters.
     */

    @Setter
    @Getter
    @Column(name = "language_code", nullable = false, length = 10)
    private String languageCode;

    /**
     * Category to group dictionary entries.
     * Optional, with a maximum length of 100 characters.
     */

    @Setter
    @Getter
    @Column(name = "category", length = 100)
    private String category;

    /**
     * Timestamp of entry creation.
     * Automatically set when the entry is first created.
     */
    @Getter
    @Column(name = "created_at", nullable = false, updatable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    /**
     * Timestamp of the last update to the entry.
     * Automatically updated when the entry is modified.
     */
    @Getter
    @Column(name = "updated_at")
    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @Size(max = 255, message = "Firebase User ID cannot exceed 255 characters")
    @Pattern(regexp = "^[a-zA-Z0-9]+$", message = "Updater ID must contain only letters and numbers")
    private String updaterId;

}
