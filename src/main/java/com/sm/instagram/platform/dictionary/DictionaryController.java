package com.sm.instagram.platform.dictionary;

import com.sm.instagram.platform.common.exceptions.ResourceNotFoundException;
import com.sm.instagram.platform.common.exceptions.ValidationTranslatableException;
import com.sm.instagram.platform.common.ratelimit.RateLimit;
import com.sm.instagram.platform.common.ratelimit.RateLimitKeyType;
import com.sm.instagram.platform.common.ratelimit.RateLimitProfile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST Controller for managing dictionary entries and translations.
 * <p>
 * Provides endpoints for retrieving translations, creating entries,
 * fetching entries by category, and managing dictionary metadata.
 *
 * @author Peter Żmudzki
 * @version 1.0
 * @since 2024-03-24
 */
@Slf4j
@RestController
@RequestMapping("/dictionary")
@RateLimit(profile = RateLimitProfile.RELAXED, keyType = RateLimitKeyType.USER_ENDPOINT)
public class DictionaryController {
    /**
     * Service for handling dictionary entry operations.
     */
    private final DictionaryService dictionaryService;

    /**
     * Constructor for dependency injection of DictionaryService.
     *
     * @param dictionaryService The service to handle dictionary operations
     */
    @Autowired
    public DictionaryController(DictionaryService dictionaryService) {
        this.dictionaryService = dictionaryService;
    }

    /**
     * Endpoint to retrieve a translation for a specific key and language.
     *
     * @param key          The key to translate
     * @param languageCode The language code for the translation
     * @return ResponseEntity with the translation
     * @throws ValidationTranslatableException if parameters are invalid
     * @throws ResourceNotFoundException       if translation not found or authentication missing
     */
    @GetMapping("/translate")
    public ResponseEntity<String> getTranslation(
            @RequestParam String key,
            @RequestParam String languageCode
    ) {
        // Validate input parameters
        validateTranslationParameters(key, languageCode);

        String firebaseUid = getAuthenticatedUserUid();
        log.info("GDPR: Operation=getTranslation, FirebaseUID={}, Key={}, Language={}, Purpose=translation_retrieval",
                firebaseUid, key, languageCode);
        log.info("Getting translation for key: {} in language: {}", key, languageCode);
        long startTime = System.currentTimeMillis();

        Optional<String> translation = dictionaryService.getTranslation(key, languageCode);
        long duration = System.currentTimeMillis() - startTime;

        if (translation.isPresent()) {
            log.info("GDPR: DataAccessed=dictionary_translation, FirebaseUID={}, Key={}, Purpose=display",
                    firebaseUid, key);
            log.info("Successfully retrieved translation for key: {} in {}ms", key, duration);
            log.debug("Translation value: {}", translation.get());
            return ResponseEntity.ok(translation.get());
        } else {
            log.warn("Translation not found for key: {} in language: {} after {}ms",
                    key, languageCode, duration);
            throw new ResourceNotFoundException("error.business.item_not_found", "Translation for key: " + key + " in language: " + languageCode);
        }
    }

    /**
     * Endpoint to create or update a dictionary entry.
     *
     * @param entry The DictionaryEntry to create or update
     * @return ResponseEntity with the saved dictionary entry
     * @throws ValidationTranslatableException if entry data is invalid
     * @throws ResourceNotFoundException       if authentication missing
     */
    // java:S4684, reviewed rather than refactored. The danger the rule names is mass
    // assignment, and this entity gives it nothing to work with: every field is a scalar, there
    // is no relation to traverse, both timestamps belong to Hibernate (@CreationTimestamp and
    // @UpdateTimestamp, with created_at updatable=false, so a posted value is ignored), and
    // updaterId is now set from the security context in the service. What is left is id, key,
    // value, languageCode and category -- which is the update contract itself, on an ADMIN-only
    // endpoint. A DTO here would be the same five fields under a second name.
    @SuppressWarnings("java:S4684")
    @PreAuthorize("hasAuthority('ADMIN')")
    @PostMapping("/entry")
    public ResponseEntity<DictionaryEntry> createEntry(@RequestBody DictionaryEntry entry) {
        // Validate entry data
        validateDictionaryEntry(entry);

        String firebaseUid = getAuthenticatedUserUid();
        log.info("GDPR: Operation=createDictionaryEntry, FirebaseUID={}, Key={}, Category={}, Purpose=translation_management",
                firebaseUid, entry.getKey(), entry.getCategory());
        log.info("Creating/updating dictionary entry with key: {} for category: {}",
                entry.getKey(), entry.getCategory());
        log.debug("Dictionary entry data: {}", entry);
        long startTime = System.currentTimeMillis();

        DictionaryEntry savedEntry = dictionaryService.createOrUpdateEntry(entry);

        long duration = System.currentTimeMillis() - startTime;
        log.info("GDPR: DataCreated=dictionary_entry, FirebaseUID={}, EntryID={}, Purpose=translation_storage",
                firebaseUid, savedEntry.getId());
        log.info("Successfully created/updated dictionary entry with ID: {} in {}ms",
                savedEntry.getId(), duration);
        log.debug("Saved entry details: key={}, category={}, languageCode={}, value={}",
                savedEntry.getKey(), savedEntry.getCategory(),
                savedEntry.getLanguageCode(), savedEntry.getValue());

        return ResponseEntity.ok(savedEntry);
    }

    /**
     * Endpoint to delete an entry
     *
     * @param id The DictionaryEntry id
     * @return ResponseEntity with no content on successful deletion
     * @throws ValidationTranslatableException if id is invalid
     * @throws ResourceNotFoundException       if authentication missing
     */
    @PreAuthorize("hasAuthority('ADMIN')")
    @DeleteMapping("/entry")
    public ResponseEntity<Void> deleteEntry(@RequestParam UUID id) {
        // Validate id parameter
        if (id == null) {
            throw new ValidationTranslatableException("error.validation.missing_parameter", "id");
        }

        String firebaseUid = getAuthenticatedUserUid();
        log.warn("GDPR: DELETION Operation=deleteDictionaryEntry, FirebaseUID={}, EntryID={}, Purpose=translation_removal",
                firebaseUid, id);
        log.info("Deleting dictionary entry with ID: {}", id);
        long startTime = System.currentTimeMillis();

        dictionaryService.deleteEntry(id);

        long duration = System.currentTimeMillis() - startTime;
        log.warn("GDPR: DELETION_COMPLETE EntryID={}, FirebaseUID={}, Permanent=true, Purpose=translation_management",
                id, firebaseUid);
        log.info("Successfully deleted dictionary entry with ID: {} in {}ms", id, duration);

        return ResponseEntity.noContent().build();
    }

    /**
     * Endpoint to retrieve all dictionary entries for a specific category.
     *
     * @param category The category to filter entries
     * @return ResponseEntity with a list of DictionaryEntry instances
     * @throws ValidationTranslatableException if category is invalid
     * @throws ResourceNotFoundException       if authentication missing
     */
    @GetMapping("/category/{category}")
    public ResponseEntity<List<DictionaryEntry>> getEntriesByCategory(@PathVariable String category) {
        // Validate category parameter
        if (category == null || category.trim().isEmpty()) {
            throw new ValidationTranslatableException("error.validation.missing_parameter", "category");
        }

        String firebaseUid = getAuthenticatedUserUid();
        log.info("GDPR: Operation=getEntriesByCategory, FirebaseUID={}, Category={}, Purpose=category_translation_retrieval",
                firebaseUid, category);
        log.info("Retrieving dictionary entries for category: {}", category);
        long startTime = System.currentTimeMillis();

        List<DictionaryEntry> entries = dictionaryService.getEntriesByCategory(category);

        long duration = System.currentTimeMillis() - startTime;
        log.info("GDPR: DataAccessed=dictionary_entries, FirebaseUID={}, RecordCount={}, Category={}, Purpose=category_listing",
                firebaseUid, entries.size(), category);
        log.info("Successfully retrieved {} dictionary entries for category: {} in {}ms",
                entries.size(), category, duration);
        log.debug("Retrieved entries: {}", entries.stream()
                .map(DictionaryEntry::getKey)
                .collect(Collectors.toList()));

        return ResponseEntity.ok(entries);
    }

    /**
     * Endpoint to retrieve all unique categories in the dictionary.
     *
     * @return ResponseEntity with a set of unique category names
     * @throws ResourceNotFoundException if authentication missing
     */
    @GetMapping("/categories")
    public ResponseEntity<Set<String>> getAllCategories() {
        String firebaseUid = getAuthenticatedUserUid();
        log.info("GDPR: Operation=getAllCategories, FirebaseUID={}, Purpose=category_enumeration", firebaseUid);
        log.info("Retrieving all dictionary categories");
        long startTime = System.currentTimeMillis();

        Set<String> categories = dictionaryService
                .findAll().stream()
                .map(DictionaryEntry::getCategory)
                .filter(category -> category != null && !category.isEmpty())
                .collect(Collectors.toSet());

        long duration = System.currentTimeMillis() - startTime;
        log.info("GDPR: DataAccessed=dictionary_categories, FirebaseUID={}, CategoryCount={}, Purpose=category_listing",
                firebaseUid, categories.size());
        log.info("Successfully retrieved {} unique dictionary categories in {}ms",
                categories.size(), duration);
        log.debug("Categories found: {}", categories);

        return ResponseEntity.ok(categories);
    }

    /**
     * Endpoint to retrieve all dictionary entries across all categories and languages.
     *
     * @return ResponseEntity with a list of all DictionaryEntry instances
     * @throws ResourceNotFoundException if authentication missing
     */
    @GetMapping("/all")
    public ResponseEntity<List<DictionaryEntry>> getAllEntries() {
        String firebaseUid = getAuthenticatedUserUid();
        log.info("GDPR: Operation=getAllDictionaryEntries, FirebaseUID={}, Purpose=full_dictionary_export", firebaseUid);
        log.info("Retrieving all dictionary entries");
        long startTime = System.currentTimeMillis();

        List<DictionaryEntry> allEntries = dictionaryService.findAll();

        long duration = System.currentTimeMillis() - startTime;
        log.info("GDPR: DataAccessed=all_dictionary_entries, FirebaseUID={}, RecordCount={}, Purpose=bulk_export",
                firebaseUid, allEntries.size());
        log.info("Successfully retrieved {} dictionary entries in {}ms",
                allEntries.size(), duration);
        log.debug("Entries span {} unique categories", allEntries.stream()
                .map(DictionaryEntry::getCategory)
                .filter(category -> category != null && !category.isEmpty())
                .collect(Collectors.toSet()).size());

        return ResponseEntity.ok(allEntries);
    }

    // ===== HELPER METHODS =====

    /**
     * Gets the authenticated user UID from the security context.
     *
     * @return The Firebase UID of the authenticated user
     * @throws ResourceNotFoundException if authentication context is missing
     */
    private String getAuthenticatedUserUid() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            log.error("Authentication not available in security context");
            throw new ResourceNotFoundException("error.auth.not_authenticated");
        }
        return auth.getPrincipal().toString();
    }

    /**
     * Validates translation request parameters.
     *
     * @param key          The translation key
     * @param languageCode The language code
     * @throws ValidationTranslatableException if parameters are invalid
     */
    private void validateTranslationParameters(String key, String languageCode) {
        if (key == null || key.trim().isEmpty()) {
            throw new ValidationTranslatableException("error.validation.missing_parameter", "key");
        }
        if (languageCode == null || languageCode.trim().isEmpty()) {
            throw new ValidationTranslatableException("error.validation.missing_parameter", "languageCode");
        }
        // Validate language code format (ISO 639-1: 2 letters)
        if (!languageCode.matches("^[a-z]{2}$")) {
            throw new ValidationTranslatableException("error.validation.invalid_parameter", "languageCode must be a 2-letter ISO code");
        }
    }

    /**
     * Validates dictionary entry data.
     *
     * @param entry The dictionary entry to validate
     * @throws ValidationTranslatableException if entry data is invalid
     */
    private void validateDictionaryEntry(DictionaryEntry entry) {
        if (entry == null) {
            throw new ValidationTranslatableException("error.validation.missing_parameter", "entry");
        }
        if (entry.getKey() == null || entry.getKey().trim().isEmpty()) {
            throw new ValidationTranslatableException("error.validation.missing_parameter", "entry.key");
        }
        if (entry.getLanguageCode() == null || entry.getLanguageCode().trim().isEmpty()) {
            throw new ValidationTranslatableException("error.validation.missing_parameter", "entry.languageCode");
        }
        if (entry.getValue() == null || entry.getValue().trim().isEmpty()) {
            throw new ValidationTranslatableException("error.validation.missing_parameter", "entry.value");
        }
        // Validate language code format
        if (!entry.getLanguageCode().matches("^[a-z]{2}$")) {
            throw new ValidationTranslatableException("error.validation.invalid_parameter", "entry.languageCode must be a 2-letter ISO code");
        }
    }
}
