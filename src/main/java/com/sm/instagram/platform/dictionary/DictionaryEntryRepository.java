package com.sm.instagram.platform.dictionary;


import com.sm.instagram.platform.common.base.BaseRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for managing DictionaryEntry entities.
 * <p>
 * Provides database operations and custom query methods for dictionary entries.
 *
 * @author Peter Żmudzki
 * @version 1.0
 * @since 2024-03-24
 */
@Repository
public interface DictionaryEntryRepository extends BaseRepository<DictionaryEntry, UUID> {
    /**
     * Finds a dictionary entry by its key and language code.
     *
     * @param key          The key of the dictionary entry
     * @param languageCode The language code of the entry
     * @return An Optional containing the matching DictionaryEntry, or empty if not found
     */
    Optional<DictionaryEntry> findByKeyAndLanguageCode(String key, String languageCode);

    /**
     * Retrieves all dictionary entries belonging to a specific category.
     *
     * @param category The category to search for
     * @return A list of DictionaryEntry instances in the given category
     */
    List<DictionaryEntry> findByCategory(String category);

    /**
     * Retrieves all dictionary entries for a specific language.
     *
     * @param languageCode The language code to filter entries
     * @return A list of DictionaryEntry instances in the specified language
     */
    List<DictionaryEntry> findByLanguageCode(String languageCode);
}
