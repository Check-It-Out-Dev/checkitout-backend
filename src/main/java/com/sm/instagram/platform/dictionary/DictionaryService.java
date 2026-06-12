package com.sm.instagram.platform.dictionary;

import com.sm.instagram.platform.dictionary.DictionaryEntry;
import com.sm.instagram.platform.dictionary.DictionaryEntryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service class for managing dictionary entries and providing translation services.
 *
 * Handles business logic for creating, retrieving, and managing dictionary entries.
 *
 * @author Peter Żmudzki
 * @version 1.0
 * @since 2024-03-24
 */
@Slf4j
@Service
public class DictionaryService {
    /** Repository for database operations on dictionary entries. */
    private final DictionaryEntryRepository repository;

    /**
     * Constructor for dependency injection of DictionaryEntryRepository.
     *
     * @param repository The repository to be used for dictionary entry operations
     */
    @Autowired
    public DictionaryService(DictionaryEntryRepository repository) {
        this.repository = repository;
    }

    /**
     * Retrieves a translation for a specific key and language.
     *
     * @param key The key to look up
     * @param languageCode The language code for the translation
     * @return An Optional containing the translation value, or empty if not found
     */
    public Optional<String> getTranslation(String key, String languageCode) {
        String firebaseUid = "SYSTEM";
        try {
            firebaseUid = SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString();
        } catch (Exception e) {
            // Use SYSTEM if no auth context
        }
        
        log.info("GDPR: Service Operation=getTranslation, FirebaseUID={}, Key={}, Language={}, Purpose=translation_lookup", 
            firebaseUid, key, languageCode);
        
        Optional<String> translation = repository.findByKeyAndLanguageCode(key, languageCode)
                .map(DictionaryEntry::getValue);
        
        if (translation.isPresent()) {
            log.info("GDPR: DataAccessed=translation_value, FirebaseUID={}, Key={}, Purpose=translation_service", 
                firebaseUid, key);
        }
        
        return translation;
    }

    /**
     * Retrieves all dictionary entries for a specific category.
     *
     * @param category The category to filter entries
     * @return A list of DictionaryEntry instances in the specified category
     */
    public List<DictionaryEntry> getEntriesByCategory(String category) {
        String firebaseUid = "SYSTEM";
        try {
            firebaseUid = SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString();
        } catch (Exception e) {
            // Use SYSTEM if no auth context
        }
        
        log.info("GDPR: Service Operation=getEntriesByCategory, FirebaseUID={}, Category={}, Purpose=category_retrieval", 
            firebaseUid, category);
        
        List<DictionaryEntry> entries = repository.findByCategory(category);
        
        log.info("GDPR: DataAccessed=category_entries, FirebaseUID={}, RecordCount={}, Purpose=translation_service", 
            firebaseUid, entries.size());
        
        return entries;
    }

    /**
     * Creates a new dictionary entry or updates an existing one.
     *
     * @param entry The DictionaryEntry to create or update
     * @return The saved DictionaryEntry (with updated ID if newly created)
     */
    public DictionaryEntry createOrUpdateEntry(DictionaryEntry entry) {
        String firebaseUid = "SYSTEM";
        try {
            firebaseUid = SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString();
        } catch (Exception e) {
            // Use SYSTEM if no auth context
        }
        
        log.info("GDPR: Service Operation=createOrUpdateEntry, FirebaseUID={}, Key={}, Category={}, Purpose=translation_management", 
            firebaseUid, entry.getKey(), entry.getCategory());
        
        DictionaryEntry saved = repository.save(entry);
        
        log.info("GDPR: DataCreated=dictionary_entry, FirebaseUID={}, EntryID={}, Purpose=translation_storage", 
            firebaseUid, saved.getId());
        
        return saved;
    }

    /**
     * Deletes existing entry
     *
     * @param id The DictionaryEntry id
     */
    public void deleteEntry(UUID id) {
        String firebaseUid = "SYSTEM";
        try {
            firebaseUid = SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString();
        } catch (Exception e) {
            // Use SYSTEM if no auth context
        }
        
        log.warn("GDPR: DELETION Service Operation=deleteEntry, FirebaseUID={}, EntryID={}, Purpose=translation_removal", 
            firebaseUid, id);
        
        repository.deleteById(id);
        
        log.warn("GDPR: DELETION_COMPLETE EntryID={}, FirebaseUID={}, Permanent=true, Purpose=data_removal", 
            id, firebaseUid);
    }

    /**
     * Retrieves all dictionary entries for a specific language.
     *
     * @param languageCode The language code to filter entries
     * @return A list of DictionaryEntry instances in the specified language
     */
    public List<DictionaryEntry> getEntriesByLanguage(String languageCode) {
        String firebaseUid = "SYSTEM";
        try {
            firebaseUid = SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString();
        } catch (Exception e) {
            // Use SYSTEM if no auth context
        }
        
        log.info("GDPR: Service Operation=getEntriesByLanguage, FirebaseUID={}, Language={}, Purpose=language_translations", 
            firebaseUid, languageCode);
        
        List<DictionaryEntry> entries = repository.findByLanguageCode(languageCode);
        
        log.info("GDPR: DataAccessed=language_entries, FirebaseUID={}, RecordCount={}, Purpose=translation_service", 
            firebaseUid, entries.size());
        
        return entries;
    }

    /**
     * Retrieves all dictionary entries.
     *
     * @return A list of all DictionaryEntry instances
     */
    public List<DictionaryEntry> findAll() {
        String firebaseUid = "SYSTEM";
        try {
            firebaseUid = SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString();
        } catch (Exception e) {
            // Use SYSTEM if no auth context
        }
        
        log.info("GDPR: Service Operation=findAllDictionaryEntries, FirebaseUID={}, Purpose=full_dictionary_retrieval", 
            firebaseUid);
        
        List<DictionaryEntry> allEntries = repository.findAll();
        
        log.info("GDPR: DataAccessed=all_dictionary_entries, FirebaseUID={}, RecordCount={}, Purpose=bulk_retrieval", 
            firebaseUid, allEntries.size());
        
        return allEntries;
    }
}
