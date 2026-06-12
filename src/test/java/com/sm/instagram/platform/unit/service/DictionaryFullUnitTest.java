package com.sm.instagram.platform.unit.service;

import com.sm.instagram.platform.common.exceptions.ResourceNotFoundException;
import com.sm.instagram.platform.common.exceptions.ValidationTranslatableException;
import com.sm.instagram.platform.dictionary.DictionaryController;
import com.sm.instagram.platform.dictionary.DictionaryEntry;
import com.sm.instagram.platform.dictionary.DictionaryEntryRepository;
import com.sm.instagram.platform.dictionary.DictionaryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for Dictionary package.
 * Tests DictionaryController, DictionaryService, and DictionaryEntry.
 * Uses pure Mockito - no Spring context loaded.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Dictionary Package Full Unit Tests")
class DictionaryFullUnitTest {

    // ========================================================================
    // DICTIONARY ENTRY ENTITY TESTS
    // ========================================================================

    @Nested
    @DisplayName("DictionaryEntry Entity Tests")
    class DictionaryEntryEntityTests {

        private DictionaryEntry entry;

        @BeforeEach
        void setUp() {
            entry = new DictionaryEntry();
        }

        @Nested
        @DisplayName("Constructor Tests")
        class ConstructorTests {

            @Test
            @DisplayName("should create entry with no-args constructor")
            void shouldCreateEntryWithNoArgsConstructor() {
                DictionaryEntry newEntry = new DictionaryEntry();
                assertThat(newEntry).isNotNull();
                assertThat(newEntry.getId()).isNull();
                assertThat(newEntry.getKey()).isNull();
            }

            @Test
            @DisplayName("should create entry with all-args constructor")
            void shouldCreateEntryWithAllArgsConstructor() {
                UUID id = UUID.randomUUID();
                LocalDateTime now = LocalDateTime.now();
                DictionaryEntry fullEntry = new DictionaryEntry(
                        id, "test.key", "Test Value", "en", "CATEGORY", now, now, "updater123"
                );

                assertThat(fullEntry.getId()).isEqualTo(id);
                assertThat(fullEntry.getKey()).isEqualTo("test.key");
                assertThat(fullEntry.getValue()).isEqualTo("Test Value");
                assertThat(fullEntry.getLanguageCode()).isEqualTo("en");
                assertThat(fullEntry.getCategory()).isEqualTo("CATEGORY");
                assertThat(fullEntry.getCreatedAt()).isEqualTo(now);
                assertThat(fullEntry.getUpdatedAt()).isEqualTo(now);
                assertThat(fullEntry.getUpdaterId()).isEqualTo("updater123");
            }
        }

        @Nested
        @DisplayName("ID Field Tests")
        class IdFieldTests {

            @Test
            @DisplayName("should set and get ID")
            void shouldSetAndGetId() {
                UUID id = UUID.randomUUID();
                entry.setId(id);
                assertThat(entry.getId()).isEqualTo(id);
            }

            @Test
            @DisplayName("should accept null ID")
            void shouldAcceptNullId() {
                entry.setId(null);
                assertThat(entry.getId()).isNull();
            }

            @Test
            @DisplayName("should accept random UUID")
            void shouldAcceptRandomUuid() {
                UUID randomId = UUID.randomUUID();
                entry.setId(randomId);
                assertThat(entry.getId()).isNotNull();
                assertThat(entry.getId().toString()).hasSize(36);
            }
        }

        @Nested
        @DisplayName("Key Field Tests")
        class KeyFieldTests {

            @Test
            @DisplayName("should set and get key")
            void shouldSetAndGetKey() {
                entry.setKey("greeting.hello");
                assertThat(entry.getKey()).isEqualTo("greeting.hello");
            }

            @Test
            @DisplayName("should accept null key")
            void shouldAcceptNullKey() {
                entry.setKey(null);
                assertThat(entry.getKey()).isNull();
            }

            @Test
            @DisplayName("should accept empty key")
            void shouldAcceptEmptyKey() {
                entry.setKey("");
                assertThat(entry.getKey()).isEmpty();
            }

            @Test
            @DisplayName("should accept key with dots")
            void shouldAcceptKeyWithDots() {
                entry.setKey("module.feature.message");
                assertThat(entry.getKey()).isEqualTo("module.feature.message");
            }

            @Test
            @DisplayName("should accept key with special characters")
            void shouldAcceptKeyWithSpecialCharacters() {
                entry.setKey("key-with_special.chars123");
                assertThat(entry.getKey()).isEqualTo("key-with_special.chars123");
            }
        }

        @Nested
        @DisplayName("Value Field Tests")
        class ValueFieldTests {

            @Test
            @DisplayName("should set and get value")
            void shouldSetAndGetValue() {
                entry.setValue("Hello World");
                assertThat(entry.getValue()).isEqualTo("Hello World");
            }

            @Test
            @DisplayName("should accept null value")
            void shouldAcceptNullValue() {
                entry.setValue(null);
                assertThat(entry.getValue()).isNull();
            }

            @Test
            @DisplayName("should accept empty value")
            void shouldAcceptEmptyValue() {
                entry.setValue("");
                assertThat(entry.getValue()).isEmpty();
            }

            @Test
            @DisplayName("should accept long value")
            void shouldAcceptLongValue() {
                String longValue = "A".repeat(1000);
                entry.setValue(longValue);
                assertThat(entry.getValue()).hasSize(1000);
            }

            @Test
            @DisplayName("should accept Unicode value")
            void shouldAcceptUnicodeValue() {
                entry.setValue("Cześć świat! Witaj! Dzień dobry!");
                assertThat(entry.getValue()).contains("Cześć");
            }

            @Test
            @DisplayName("should accept value with emojis")
            void shouldAcceptValueWithEmojis() {
                entry.setValue("Hello World!");
                assertThat(entry.getValue()).isEqualTo("Hello World!");
            }
        }

        @Nested
        @DisplayName("LanguageCode Field Tests")
        class LanguageCodeFieldTests {

            @Test
            @DisplayName("should set and get language code")
            void shouldSetAndGetLanguageCode() {
                entry.setLanguageCode("en");
                assertThat(entry.getLanguageCode()).isEqualTo("en");
            }

            @Test
            @DisplayName("should accept Polish language code")
            void shouldAcceptPolishLanguageCode() {
                entry.setLanguageCode("pl");
                assertThat(entry.getLanguageCode()).isEqualTo("pl");
            }

            @Test
            @DisplayName("should accept null language code")
            void shouldAcceptNullLanguageCode() {
                entry.setLanguageCode(null);
                assertThat(entry.getLanguageCode()).isNull();
            }

            @Test
            @DisplayName("should accept various ISO codes")
            void shouldAcceptVariousIsoCodes() {
                String[] codes = {"en", "pl", "de", "fr", "es", "it", "pt", "ru", "zh", "ja"};
                for (String code : codes) {
                    entry.setLanguageCode(code);
                    assertThat(entry.getLanguageCode()).isEqualTo(code);
                }
            }
        }

        @Nested
        @DisplayName("Category Field Tests")
        class CategoryFieldTests {

            @Test
            @DisplayName("should set and get category")
            void shouldSetAndGetCategory() {
                entry.setCategory("ERRORS");
                assertThat(entry.getCategory()).isEqualTo("ERRORS");
            }

            @Test
            @DisplayName("should accept null category")
            void shouldAcceptNullCategory() {
                entry.setCategory(null);
                assertThat(entry.getCategory()).isNull();
            }

            @Test
            @DisplayName("should accept empty category")
            void shouldAcceptEmptyCategory() {
                entry.setCategory("");
                assertThat(entry.getCategory()).isEmpty();
            }

            @Test
            @DisplayName("should accept various category names")
            void shouldAcceptVariousCategoryNames() {
                String[] categories = {"ERRORS", "MESSAGES", "UI", "VALIDATION", "SYSTEM"};
                for (String category : categories) {
                    entry.setCategory(category);
                    assertThat(entry.getCategory()).isEqualTo(category);
                }
            }
        }

        @Nested
        @DisplayName("Timestamp Field Tests")
        class TimestampFieldTests {

            @Test
            @DisplayName("should set and get createdAt")
            void shouldSetAndGetCreatedAt() {
                LocalDateTime now = LocalDateTime.now();
                entry.setCreatedAt(now);
                assertThat(entry.getCreatedAt()).isEqualTo(now);
            }

            @Test
            @DisplayName("should set and get updatedAt")
            void shouldSetAndGetUpdatedAt() {
                LocalDateTime now = LocalDateTime.now();
                entry.setUpdatedAt(now);
                assertThat(entry.getUpdatedAt()).isEqualTo(now);
            }

            @Test
            @DisplayName("should accept null timestamps")
            void shouldAcceptNullTimestamps() {
                entry.setCreatedAt(null);
                entry.setUpdatedAt(null);
                assertThat(entry.getCreatedAt()).isNull();
                assertThat(entry.getUpdatedAt()).isNull();
            }
        }

        @Nested
        @DisplayName("UpdaterTracking Interface Tests")
        class UpdaterTrackingTests {

            @Test
            @DisplayName("should set and get updaterId")
            void shouldSetAndGetUpdaterId() {
                entry.setUpdaterId("user123ABC");
                assertThat(entry.getUpdaterId()).isEqualTo("user123ABC");
            }

            @Test
            @DisplayName("should accept null updaterId")
            void shouldAcceptNullUpdaterId() {
                entry.setUpdaterId(null);
                assertThat(entry.getUpdaterId()).isNull();
            }

            @Test
            @DisplayName("should implement setAutoUpdaterId via default method")
            void shouldImplementSetAutoUpdaterId() {
                entry.setAutoUpdaterId("autoUser456");
                assertThat(entry.getUpdaterId()).isEqualTo("autoUser456");
            }
        }
    }

    // ========================================================================
    // DICTIONARY SERVICE TESTS
    // ========================================================================

    @Nested
    @DisplayName("DictionaryService Tests")
    class DictionaryServiceTests {

        @Mock
        private DictionaryEntryRepository repository;

        @InjectMocks
        private DictionaryService service;

        private DictionaryEntry testEntry;
        private UUID testId;

        @BeforeEach
        void setUp() {
            testId = UUID.randomUUID();
            testEntry = createTestEntry(testId, "test.key", "Test Value", "en", "TEST");
        }

        @Nested
        @DisplayName("getTranslation Tests")
        class GetTranslationTests {

            @Test
            @DisplayName("should return translation when found")
            void shouldReturnTranslationWhenFound() {
                when(repository.findByKeyAndLanguageCode("test.key", "en"))
                        .thenReturn(Optional.of(testEntry));

                Optional<String> result = service.getTranslation("test.key", "en");

                assertThat(result).isPresent().contains("Test Value");
                verify(repository).findByKeyAndLanguageCode("test.key", "en");
            }

            @Test
            @DisplayName("should return empty when translation not found")
            void shouldReturnEmptyWhenNotFound() {
                when(repository.findByKeyAndLanguageCode("unknown", "en"))
                        .thenReturn(Optional.empty());

                Optional<String> result = service.getTranslation("unknown", "en");

                assertThat(result).isEmpty();
            }

            @Test
            @DisplayName("should handle different language codes")
            void shouldHandleDifferentLanguageCodes() {
                DictionaryEntry plEntry = createTestEntry(UUID.randomUUID(), "greeting", "Cześć", "pl", "GREETINGS");
                when(repository.findByKeyAndLanguageCode("greeting", "pl"))
                        .thenReturn(Optional.of(plEntry));

                Optional<String> result = service.getTranslation("greeting", "pl");

                assertThat(result).isPresent().contains("Cześć");
            }

            @Test
            @DisplayName("should handle null key")
            void shouldHandleNullKey() {
                when(repository.findByKeyAndLanguageCode(null, "en"))
                        .thenReturn(Optional.empty());

                Optional<String> result = service.getTranslation(null, "en");

                assertThat(result).isEmpty();
            }
        }

        @Nested
        @DisplayName("getEntriesByCategory Tests")
        class GetEntriesByCategoryTests {

            @Test
            @DisplayName("should return entries for category")
            void shouldReturnEntriesForCategory() {
                List<DictionaryEntry> entries = List.of(testEntry);
                when(repository.findByCategory("TEST")).thenReturn(entries);

                List<DictionaryEntry> result = service.getEntriesByCategory("TEST");

                assertThat(result).hasSize(1);
                assertThat(result.get(0).getKey()).isEqualTo("test.key");
            }

            @Test
            @DisplayName("should return empty list when no entries")
            void shouldReturnEmptyListWhenNoEntries() {
                when(repository.findByCategory("EMPTY")).thenReturn(Collections.emptyList());

                List<DictionaryEntry> result = service.getEntriesByCategory("EMPTY");

                assertThat(result).isEmpty();
            }

            @Test
            @DisplayName("should return multiple entries")
            void shouldReturnMultipleEntries() {
                List<DictionaryEntry> entries = List.of(
                        createTestEntry(UUID.randomUUID(), "key1", "val1", "en", "CAT"),
                        createTestEntry(UUID.randomUUID(), "key2", "val2", "en", "CAT"),
                        createTestEntry(UUID.randomUUID(), "key3", "val3", "pl", "CAT")
                );
                when(repository.findByCategory("CAT")).thenReturn(entries);

                List<DictionaryEntry> result = service.getEntriesByCategory("CAT");

                assertThat(result).hasSize(3);
            }
        }

        @Nested
        @DisplayName("createOrUpdateEntry Tests")
        class CreateOrUpdateEntryTests {

            @Test
            @DisplayName("should save new entry")
            void shouldSaveNewEntry() {
                DictionaryEntry newEntry = createTestEntry(null, "new.key", "New Value", "en", "NEW");
                DictionaryEntry savedEntry = createTestEntry(UUID.randomUUID(), "new.key", "New Value", "en", "NEW");
                when(repository.save(any())).thenReturn(savedEntry);

                DictionaryEntry result = service.createOrUpdateEntry(newEntry);

                assertThat(result.getId()).isNotNull();
                verify(repository).save(newEntry);
            }

            @Test
            @DisplayName("should update existing entry")
            void shouldUpdateExistingEntry() {
                testEntry.setValue("Updated Value");
                when(repository.save(testEntry)).thenReturn(testEntry);

                DictionaryEntry result = service.createOrUpdateEntry(testEntry);

                assertThat(result.getValue()).isEqualTo("Updated Value");
            }

            @Test
            @DisplayName("should preserve all fields")
            void shouldPreserveAllFields() {
                when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

                DictionaryEntry result = service.createOrUpdateEntry(testEntry);

                assertThat(result.getKey()).isEqualTo("test.key");
                assertThat(result.getValue()).isEqualTo("Test Value");
                assertThat(result.getLanguageCode()).isEqualTo("en");
                assertThat(result.getCategory()).isEqualTo("TEST");
            }
        }

        @Nested
        @DisplayName("deleteEntry Tests")
        class DeleteEntryTests {

            @Test
            @DisplayName("should delete entry by ID")
            void shouldDeleteEntryById() {
                doNothing().when(repository).deleteById(testId);

                service.deleteEntry(testId);

                verify(repository).deleteById(testId);
            }

            @Test
            @DisplayName("should call repository delete once")
            void shouldCallRepositoryDeleteOnce() {
                UUID id = UUID.randomUUID();
                doNothing().when(repository).deleteById(id);

                service.deleteEntry(id);

                verify(repository, times(1)).deleteById(id);
            }
        }

        @Nested
        @DisplayName("getEntriesByLanguage Tests")
        class GetEntriesByLanguageTests {

            @Test
            @DisplayName("should return English entries")
            void shouldReturnEnglishEntries() {
                List<DictionaryEntry> englishEntries = List.of(
                        createTestEntry(UUID.randomUUID(), "key1", "Value 1", "en", "CAT"),
                        createTestEntry(UUID.randomUUID(), "key2", "Value 2", "en", "CAT")
                );
                when(repository.findByLanguageCode("en")).thenReturn(englishEntries);

                List<DictionaryEntry> result = service.getEntriesByLanguage("en");

                assertThat(result).hasSize(2);
                assertThat(result).allMatch(e -> "en".equals(e.getLanguageCode()));
            }

            @Test
            @DisplayName("should return Polish entries")
            void shouldReturnPolishEntries() {
                List<DictionaryEntry> polishEntries = List.of(
                        createTestEntry(UUID.randomUUID(), "powitanie", "Cześć", "pl", "GREET")
                );
                when(repository.findByLanguageCode("pl")).thenReturn(polishEntries);

                List<DictionaryEntry> result = service.getEntriesByLanguage("pl");

                assertThat(result).hasSize(1);
                assertThat(result.get(0).getValue()).isEqualTo("Cześć");
            }

            @Test
            @DisplayName("should return empty for unsupported language")
            void shouldReturnEmptyForUnsupportedLanguage() {
                when(repository.findByLanguageCode("xx")).thenReturn(Collections.emptyList());

                List<DictionaryEntry> result = service.getEntriesByLanguage("xx");

                assertThat(result).isEmpty();
            }
        }

        @Nested
        @DisplayName("findAll Tests")
        class FindAllTests {

            @Test
            @DisplayName("should return all entries")
            void shouldReturnAllEntries() {
                List<DictionaryEntry> allEntries = List.of(
                        createTestEntry(UUID.randomUUID(), "key1", "val1", "en", "CAT1"),
                        createTestEntry(UUID.randomUUID(), "key2", "val2", "pl", "CAT2")
                );
                when(repository.findAll()).thenReturn(allEntries);

                List<DictionaryEntry> result = service.findAll();

                assertThat(result).hasSize(2);
            }

            @Test
            @DisplayName("should return empty when no entries")
            void shouldReturnEmptyWhenNoEntries() {
                when(repository.findAll()).thenReturn(Collections.emptyList());

                List<DictionaryEntry> result = service.findAll();

                assertThat(result).isEmpty();
            }
        }

        private DictionaryEntry createTestEntry(UUID id, String key, String value, String langCode, String category) {
            DictionaryEntry entry = new DictionaryEntry();
            entry.setId(id);
            entry.setKey(key);
            entry.setValue(value);
            entry.setLanguageCode(langCode);
            entry.setCategory(category);
            return entry;
        }
    }

    // ========================================================================
    // DICTIONARY CONTROLLER TESTS
    // ========================================================================

    @Nested
    @DisplayName("DictionaryController Tests")
    class DictionaryControllerTests {

        @Mock
        private DictionaryService dictionaryService;

        @Mock
        private SecurityContext securityContext;

        @Mock
        private Authentication authentication;

        @InjectMocks
        private DictionaryController controller;

        private DictionaryEntry testEntry;
        private UUID testId;

        @BeforeEach
        void setUp() {
            testId = UUID.randomUUID();
            testEntry = createTestEntry(testId, "test.key", "Test Value", "en", "TEST");

            // Setup security context
            when(authentication.getPrincipal()).thenReturn("firebase-uid-123");
            when(securityContext.getAuthentication()).thenReturn(authentication);
            SecurityContextHolder.setContext(securityContext);
        }

        @Nested
        @DisplayName("getTranslation Endpoint Tests")
        class GetTranslationEndpointTests {

            @Test
            @DisplayName("should return translation successfully")
            void shouldReturnTranslationSuccessfully() {
                when(dictionaryService.getTranslation("test.key", "en"))
                        .thenReturn(Optional.of("Test Value"));

                ResponseEntity<String> response = controller.getTranslation("test.key", "en");

                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(response.getBody()).isEqualTo("Test Value");
            }

            @Test
            @DisplayName("should throw ResourceNotFoundException when translation not found")
            void shouldThrowWhenTranslationNotFound() {
                when(dictionaryService.getTranslation("missing.key", "en"))
                        .thenReturn(Optional.empty());

                assertThatThrownBy(() -> controller.getTranslation("missing.key", "en"))
                        .isInstanceOf(ResourceNotFoundException.class);
            }

            @Test
            @DisplayName("should throw ValidationException for null key")
            void shouldThrowForNullKey() {
                assertThatThrownBy(() -> controller.getTranslation(null, "en"))
                        .isInstanceOf(ValidationTranslatableException.class);
            }

            @Test
            @DisplayName("should throw ValidationException for empty key")
            void shouldThrowForEmptyKey() {
                assertThatThrownBy(() -> controller.getTranslation("", "en"))
                        .isInstanceOf(ValidationTranslatableException.class);
            }

            @Test
            @DisplayName("should throw ValidationException for blank key")
            void shouldThrowForBlankKey() {
                assertThatThrownBy(() -> controller.getTranslation("   ", "en"))
                        .isInstanceOf(ValidationTranslatableException.class);
            }

            @Test
            @DisplayName("should throw ValidationException for null language code")
            void shouldThrowForNullLanguageCode() {
                assertThatThrownBy(() -> controller.getTranslation("test.key", null))
                        .isInstanceOf(ValidationTranslatableException.class);
            }

            @Test
            @DisplayName("should throw ValidationException for empty language code")
            void shouldThrowForEmptyLanguageCode() {
                assertThatThrownBy(() -> controller.getTranslation("test.key", ""))
                        .isInstanceOf(ValidationTranslatableException.class);
            }

            @Test
            @DisplayName("should throw ValidationException for invalid language code format")
            void shouldThrowForInvalidLanguageCodeFormat() {
                assertThatThrownBy(() -> controller.getTranslation("test.key", "ENG"))
                        .isInstanceOf(ValidationTranslatableException.class);
            }

            @Test
            @DisplayName("should throw ValidationException for uppercase language code")
            void shouldThrowForUppercaseLanguageCode() {
                assertThatThrownBy(() -> controller.getTranslation("test.key", "EN"))
                        .isInstanceOf(ValidationTranslatableException.class);
            }

            @ParameterizedTest
            @ValueSource(strings = {"abc", "123", "e1", "1e", "a", "eng", "engl"})
            @DisplayName("should throw ValidationException for various invalid language codes")
            void shouldThrowForVariousInvalidLanguageCodes(String invalidCode) {
                assertThatThrownBy(() -> controller.getTranslation("test.key", invalidCode))
                        .isInstanceOf(ValidationTranslatableException.class);
            }

            @Test
            @DisplayName("should throw ResourceNotFoundException when authentication missing")
            void shouldThrowWhenAuthenticationMissing() {
                when(securityContext.getAuthentication()).thenReturn(null);

                assertThatThrownBy(() -> controller.getTranslation("test.key", "en"))
                        .isInstanceOf(ResourceNotFoundException.class);
            }
        }

        @Nested
        @DisplayName("createEntry Endpoint Tests")
        class CreateEntryEndpointTests {

            @Test
            @DisplayName("should create entry successfully")
            void shouldCreateEntrySuccessfully() {
                when(dictionaryService.createOrUpdateEntry(any())).thenReturn(testEntry);

                ResponseEntity<DictionaryEntry> response = controller.createEntry(testEntry);

                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(response.getBody()).isNotNull();
                assertThat(response.getBody().getKey()).isEqualTo("test.key");
            }

            @Test
            @DisplayName("should throw ValidationException for null entry")
            void shouldThrowForNullEntry() {
                assertThatThrownBy(() -> controller.createEntry(null))
                        .isInstanceOf(ValidationTranslatableException.class);
            }

            @Test
            @DisplayName("should throw ValidationException for null key in entry")
            void shouldThrowForNullKeyInEntry() {
                DictionaryEntry invalidEntry = new DictionaryEntry();
                invalidEntry.setKey(null);
                invalidEntry.setValue("Some Value");
                invalidEntry.setLanguageCode("en");

                assertThatThrownBy(() -> controller.createEntry(invalidEntry))
                        .isInstanceOf(ValidationTranslatableException.class);
            }

            @Test
            @DisplayName("should throw ValidationException for empty key in entry")
            void shouldThrowForEmptyKeyInEntry() {
                DictionaryEntry invalidEntry = new DictionaryEntry();
                invalidEntry.setKey("");
                invalidEntry.setValue("Some Value");
                invalidEntry.setLanguageCode("en");

                assertThatThrownBy(() -> controller.createEntry(invalidEntry))
                        .isInstanceOf(ValidationTranslatableException.class);
            }

            @Test
            @DisplayName("should throw ValidationException for null value in entry")
            void shouldThrowForNullValueInEntry() {
                DictionaryEntry invalidEntry = new DictionaryEntry();
                invalidEntry.setKey("valid.key");
                invalidEntry.setValue(null);
                invalidEntry.setLanguageCode("en");

                assertThatThrownBy(() -> controller.createEntry(invalidEntry))
                        .isInstanceOf(ValidationTranslatableException.class);
            }

            @Test
            @DisplayName("should throw ValidationException for empty value in entry")
            void shouldThrowForEmptyValueInEntry() {
                DictionaryEntry invalidEntry = new DictionaryEntry();
                invalidEntry.setKey("valid.key");
                invalidEntry.setValue("");
                invalidEntry.setLanguageCode("en");

                assertThatThrownBy(() -> controller.createEntry(invalidEntry))
                        .isInstanceOf(ValidationTranslatableException.class);
            }

            @Test
            @DisplayName("should throw ValidationException for null language code in entry")
            void shouldThrowForNullLanguageCodeInEntry() {
                DictionaryEntry invalidEntry = new DictionaryEntry();
                invalidEntry.setKey("valid.key");
                invalidEntry.setValue("Some Value");
                invalidEntry.setLanguageCode(null);

                assertThatThrownBy(() -> controller.createEntry(invalidEntry))
                        .isInstanceOf(ValidationTranslatableException.class);
            }

            @Test
            @DisplayName("should throw ValidationException for invalid language code in entry")
            void shouldThrowForInvalidLanguageCodeInEntry() {
                DictionaryEntry invalidEntry = new DictionaryEntry();
                invalidEntry.setKey("valid.key");
                invalidEntry.setValue("Some Value");
                invalidEntry.setLanguageCode("ENG");

                assertThatThrownBy(() -> controller.createEntry(invalidEntry))
                        .isInstanceOf(ValidationTranslatableException.class);
            }
        }

        @Nested
        @DisplayName("deleteEntry Endpoint Tests")
        class DeleteEntryEndpointTests {

            @Test
            @DisplayName("should delete entry successfully")
            void shouldDeleteEntrySuccessfully() {
                doNothing().when(dictionaryService).deleteEntry(testId);

                ResponseEntity<Void> response = controller.deleteEntry(testId);

                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
                verify(dictionaryService).deleteEntry(testId);
            }

            @Test
            @DisplayName("should throw ValidationException for null id")
            void shouldThrowForNullId() {
                assertThatThrownBy(() -> controller.deleteEntry(null))
                        .isInstanceOf(ValidationTranslatableException.class);
            }
        }

        @Nested
        @DisplayName("getEntriesByCategory Endpoint Tests")
        class GetEntriesByCategoryEndpointTests {

            @Test
            @DisplayName("should return entries for category")
            void shouldReturnEntriesForCategory() {
                List<DictionaryEntry> entries = List.of(testEntry);
                when(dictionaryService.getEntriesByCategory("TEST")).thenReturn(entries);

                ResponseEntity<List<DictionaryEntry>> response = controller.getEntriesByCategory("TEST");

                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(response.getBody()).hasSize(1);
            }

            @Test
            @DisplayName("should throw ValidationException for null category")
            void shouldThrowForNullCategory() {
                assertThatThrownBy(() -> controller.getEntriesByCategory(null))
                        .isInstanceOf(ValidationTranslatableException.class);
            }

            @Test
            @DisplayName("should throw ValidationException for empty category")
            void shouldThrowForEmptyCategory() {
                assertThatThrownBy(() -> controller.getEntriesByCategory(""))
                        .isInstanceOf(ValidationTranslatableException.class);
            }

            @Test
            @DisplayName("should throw ValidationException for blank category")
            void shouldThrowForBlankCategory() {
                assertThatThrownBy(() -> controller.getEntriesByCategory("   "))
                        .isInstanceOf(ValidationTranslatableException.class);
            }

            @Test
            @DisplayName("should return empty list for unknown category")
            void shouldReturnEmptyListForUnknownCategory() {
                when(dictionaryService.getEntriesByCategory("UNKNOWN")).thenReturn(Collections.emptyList());

                ResponseEntity<List<DictionaryEntry>> response = controller.getEntriesByCategory("UNKNOWN");

                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(response.getBody()).isEmpty();
            }
        }

        @Nested
        @DisplayName("getAllCategories Endpoint Tests")
        class GetAllCategoriesEndpointTests {

            @Test
            @DisplayName("should return all categories")
            void shouldReturnAllCategories() {
                List<DictionaryEntry> entries = List.of(
                        createTestEntry(UUID.randomUUID(), "k1", "v1", "en", "CAT1"),
                        createTestEntry(UUID.randomUUID(), "k2", "v2", "en", "CAT2"),
                        createTestEntry(UUID.randomUUID(), "k3", "v3", "pl", "CAT1")
                );
                when(dictionaryService.findAll()).thenReturn(entries);

                ResponseEntity<Set<String>> response = controller.getAllCategories();

                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(response.getBody()).containsExactlyInAnyOrder("CAT1", "CAT2");
            }

            @Test
            @DisplayName("should return empty set when no entries")
            void shouldReturnEmptySetWhenNoEntries() {
                when(dictionaryService.findAll()).thenReturn(Collections.emptyList());

                ResponseEntity<Set<String>> response = controller.getAllCategories();

                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(response.getBody()).isEmpty();
            }

            @Test
            @DisplayName("should filter out null categories")
            void shouldFilterOutNullCategories() {
                DictionaryEntry entryWithNullCat = createTestEntry(UUID.randomUUID(), "k1", "v1", "en", null);
                DictionaryEntry entryWithCat = createTestEntry(UUID.randomUUID(), "k2", "v2", "en", "VALID");
                when(dictionaryService.findAll()).thenReturn(List.of(entryWithNullCat, entryWithCat));

                ResponseEntity<Set<String>> response = controller.getAllCategories();

                assertThat(response.getBody()).containsExactly("VALID");
            }

            @Test
            @DisplayName("should filter out empty categories")
            void shouldFilterOutEmptyCategories() {
                DictionaryEntry entryWithEmptyCat = createTestEntry(UUID.randomUUID(), "k1", "v1", "en", "");
                DictionaryEntry entryWithCat = createTestEntry(UUID.randomUUID(), "k2", "v2", "en", "VALID");
                when(dictionaryService.findAll()).thenReturn(List.of(entryWithEmptyCat, entryWithCat));

                ResponseEntity<Set<String>> response = controller.getAllCategories();

                assertThat(response.getBody()).containsExactly("VALID");
            }
        }

        @Nested
        @DisplayName("getAllEntries Endpoint Tests")
        class GetAllEntriesEndpointTests {

            @Test
            @DisplayName("should return all entries")
            void shouldReturnAllEntries() {
                List<DictionaryEntry> entries = List.of(
                        createTestEntry(UUID.randomUUID(), "k1", "v1", "en", "CAT1"),
                        createTestEntry(UUID.randomUUID(), "k2", "v2", "pl", "CAT2")
                );
                when(dictionaryService.findAll()).thenReturn(entries);

                ResponseEntity<List<DictionaryEntry>> response = controller.getAllEntries();

                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(response.getBody()).hasSize(2);
            }

            @Test
            @DisplayName("should return empty list when no entries")
            void shouldReturnEmptyListWhenNoEntries() {
                when(dictionaryService.findAll()).thenReturn(Collections.emptyList());

                ResponseEntity<List<DictionaryEntry>> response = controller.getAllEntries();

                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(response.getBody()).isEmpty();
            }
        }

        @Nested
        @DisplayName("Authentication Tests")
        class AuthenticationTests {

            @Test
            @DisplayName("should throw when authentication is null")
            void shouldThrowWhenAuthenticationIsNull() {
                when(securityContext.getAuthentication()).thenReturn(null);

                assertThatThrownBy(() -> controller.getTranslation("key", "en"))
                        .isInstanceOf(ResourceNotFoundException.class);
            }

            @Test
            @DisplayName("should throw when principal is null")
            void shouldThrowWhenPrincipalIsNull() {
                when(authentication.getPrincipal()).thenReturn(null);

                assertThatThrownBy(() -> controller.getTranslation("key", "en"))
                        .isInstanceOf(ResourceNotFoundException.class);
            }
        }

        private DictionaryEntry createTestEntry(UUID id, String key, String value, String langCode, String category) {
            DictionaryEntry entry = new DictionaryEntry();
            entry.setId(id);
            entry.setKey(key);
            entry.setValue(value);
            entry.setLanguageCode(langCode);
            entry.setCategory(category);
            return entry;
        }
    }

    // ========================================================================
    // INTEGRATION-LIKE BEHAVIOR TESTS (Still Unit Tests - No Spring Context)
    // ========================================================================

    @Nested
    @DisplayName("Controller-Service Integration Behavior Tests")
    class ControllerServiceBehaviorTests {

        @Mock
        private DictionaryService dictionaryService;

        @Mock
        private SecurityContext securityContext;

        @Mock
        private Authentication authentication;

        @InjectMocks
        private DictionaryController controller;

        @BeforeEach
        void setUp() {
            when(authentication.getPrincipal()).thenReturn("test-user-uid");
            when(securityContext.getAuthentication()).thenReturn(authentication);
            SecurityContextHolder.setContext(securityContext);
        }

        @Test
        @DisplayName("should handle complete translation workflow")
        void shouldHandleCompleteTranslationWorkflow() {
            // Create entry
            DictionaryEntry entry = new DictionaryEntry();
            entry.setId(UUID.randomUUID());
            entry.setKey("workflow.test");
            entry.setValue("Workflow Test Value");
            entry.setLanguageCode("en");
            entry.setCategory("WORKFLOW");

            when(dictionaryService.createOrUpdateEntry(any())).thenReturn(entry);
            when(dictionaryService.getTranslation("workflow.test", "en"))
                    .thenReturn(Optional.of("Workflow Test Value"));

            // Execute workflow
            ResponseEntity<DictionaryEntry> createResponse = controller.createEntry(entry);
            ResponseEntity<String> translationResponse = controller.getTranslation("workflow.test", "en");

            // Verify
            assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(translationResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(translationResponse.getBody()).isEqualTo("Workflow Test Value");
        }

        @Test
        @DisplayName("should handle category listing workflow")
        void shouldHandleCategoryListingWorkflow() {
            List<DictionaryEntry> entries = List.of(
                    createEntry("k1", "v1", "en", "ERRORS"),
                    createEntry("k2", "v2", "en", "MESSAGES"),
                    createEntry("k3", "v3", "pl", "ERRORS")
            );
            when(dictionaryService.findAll()).thenReturn(entries);
            when(dictionaryService.getEntriesByCategory("ERRORS")).thenReturn(
                    entries.stream().filter(e -> "ERRORS".equals(e.getCategory())).toList()
            );

            // Get all categories
            ResponseEntity<Set<String>> categoriesResponse = controller.getAllCategories();
            assertThat(categoriesResponse.getBody()).containsExactlyInAnyOrder("ERRORS", "MESSAGES");

            // Get entries for specific category
            ResponseEntity<List<DictionaryEntry>> categoryEntriesResponse = controller.getEntriesByCategory("ERRORS");
            assertThat(categoryEntriesResponse.getBody()).hasSize(2);
        }

        private DictionaryEntry createEntry(String key, String value, String langCode, String category) {
            DictionaryEntry entry = new DictionaryEntry();
            entry.setId(UUID.randomUUID());
            entry.setKey(key);
            entry.setValue(value);
            entry.setLanguageCode(langCode);
            entry.setCategory(category);
            return entry;
        }
    }

    // ========================================================================
    // EDGE CASE AND BOUNDARY TESTS
    // ========================================================================

    @Nested
    @DisplayName("Edge Case and Boundary Tests")
    class EdgeCaseTests {

        @Nested
        @DisplayName("DictionaryEntry Boundary Tests")
        class EntryBoundaryTests {

            @Test
            @DisplayName("should handle maximum length value")
            void shouldHandleMaxLengthValue() {
                DictionaryEntry entry = new DictionaryEntry();
                String maxValue = "A".repeat(1000);
                entry.setValue(maxValue);
                assertThat(entry.getValue()).hasSize(1000);
            }

            @Test
            @DisplayName("should handle maximum length category")
            void shouldHandleMaxLengthCategory() {
                DictionaryEntry entry = new DictionaryEntry();
                String maxCategory = "C".repeat(100);
                entry.setCategory(maxCategory);
                assertThat(entry.getCategory()).hasSize(100);
            }

            @Test
            @DisplayName("should handle special characters in all fields")
            void shouldHandleSpecialCharactersInAllFields() {
                DictionaryEntry entry = new DictionaryEntry();
                entry.setKey("key.with-special_chars.123");
                entry.setValue("Value with <html> & special 'chars' \"quoted\"");
                entry.setCategory("SPECIAL-CATEGORY_123");
                entry.setLanguageCode("en");

                assertThat(entry.getKey()).contains("-", "_", ".");
                assertThat(entry.getValue()).contains("<", "&", "'", "\"");
            }

            @Test
            @DisplayName("should handle whitespace in value")
            void shouldHandleWhitespaceInValue() {
                DictionaryEntry entry = new DictionaryEntry();
                entry.setValue("  Value with  spaces  ");
                assertThat(entry.getValue()).isEqualTo("  Value with  spaces  ");
            }

            @Test
            @DisplayName("should handle newlines in value")
            void shouldHandleNewlinesInValue() {
                DictionaryEntry entry = new DictionaryEntry();
                entry.setValue("Line 1\nLine 2\nLine 3");
                assertThat(entry.getValue()).contains("\n");
            }

            @Test
            @DisplayName("should handle tabs in value")
            void shouldHandleTabsInValue() {
                DictionaryEntry entry = new DictionaryEntry();
                entry.setValue("Column1\tColumn2\tColumn3");
                assertThat(entry.getValue()).contains("\t");
            }
        }

        @Nested
        @DisplayName("Language Code Boundary Tests")
        class LanguageCodeBoundaryTests {

            @Test
            @DisplayName("should accept all valid ISO 639-1 codes")
            void shouldAcceptAllValidIsoCodes() {
                String[] validCodes = {"aa", "ab", "af", "ak", "sq", "am", "ar", "an", "hy", "as",
                        "av", "ae", "ay", "az", "bm", "ba", "eu", "be", "bn", "bi", "bs", "br",
                        "bg", "my", "ca", "ch", "ce", "ny", "zh", "cu", "cv", "kw", "co", "cr",
                        "hr", "cs", "da", "dv", "nl", "dz", "en", "eo", "et", "ee", "fo", "fj",
                        "fi", "fr", "fy", "ff", "gl", "ka", "de", "el", "gn", "gu", "ht", "ha",
                        "he", "hz", "hi", "ho", "hu", "ia", "id", "ie", "ga", "ig", "ik", "io",
                        "is", "it", "iu", "ja", "jv", "kl", "kn", "kr", "ks", "kk", "km", "ki",
                        "rw", "ky", "kv", "kg", "ko", "ku", "kj", "la", "lb", "lg", "li", "ln",
                        "lo", "lt", "lu", "lv", "gv", "mk", "mg", "ms", "ml", "mt", "mi", "mr",
                        "mh", "mn", "na", "nv", "nd", "ne", "ng", "nb", "nn", "no", "ii", "nr",
                        "oc", "oj", "cu", "om", "or", "os", "pa", "pi", "fa", "pl", "ps", "pt",
                        "qu", "rm", "rn", "ro", "ru", "sa", "sc", "sd", "se", "sm", "sg", "sr",
                        "gd", "sn", "si", "sk", "sl", "so", "st", "es", "su", "sw", "ss", "sv",
                        "ta", "te", "tg", "th", "ti", "bo", "tk", "tl", "tn", "to", "tr", "ts",
                        "tt", "tw", "ty", "ug", "uk", "ur", "uz", "ve", "vi", "vo", "wa", "cy",
                        "wo", "xh", "yi", "yo", "za", "zu"};

                DictionaryEntry entry = new DictionaryEntry();
                for (String code : validCodes) {
                    entry.setLanguageCode(code);
                    assertThat(entry.getLanguageCode()).isEqualTo(code);
                }
            }
        }
    }

    // ========================================================================
    // CONCURRENT ACCESS SIMULATION TESTS
    // ========================================================================

    @Nested
    @DisplayName("Concurrent Access Simulation Tests")
    class ConcurrentAccessTests {

        @Mock
        private DictionaryEntryRepository repository;

        @InjectMocks
        private DictionaryService service;

        @Test
        @DisplayName("should handle multiple simultaneous lookups")
        void shouldHandleMultipleSimultaneousLookups() {
            when(repository.findByKeyAndLanguageCode("key1", "en"))
                    .thenReturn(Optional.of(createEntry("key1", "Value 1", "en")));
            when(repository.findByKeyAndLanguageCode("key2", "en"))
                    .thenReturn(Optional.of(createEntry("key2", "Value 2", "en")));
            when(repository.findByKeyAndLanguageCode("key3", "pl"))
                    .thenReturn(Optional.of(createEntry("key3", "Wartość 3", "pl")));

            Optional<String> result1 = service.getTranslation("key1", "en");
            Optional<String> result2 = service.getTranslation("key2", "en");
            Optional<String> result3 = service.getTranslation("key3", "pl");

            assertThat(result1).contains("Value 1");
            assertThat(result2).contains("Value 2");
            assertThat(result3).contains("Wartość 3");
        }

        @Test
        @DisplayName("should handle rapid create operations")
        void shouldHandleRapidCreateOperations() {
            when(repository.save(any())).thenAnswer(inv -> {
                DictionaryEntry e = inv.getArgument(0);
                if (e.getId() == null) {
                    e.setId(UUID.randomUUID());
                }
                return e;
            });

            for (int i = 0; i < 10; i++) {
                DictionaryEntry entry = createEntry("rapid.key." + i, "Value " + i, "en");
                DictionaryEntry saved = service.createOrUpdateEntry(entry);
                assertThat(saved.getId()).isNotNull();
            }

            verify(repository, times(10)).save(any());
        }

        private DictionaryEntry createEntry(String key, String value, String langCode) {
            DictionaryEntry entry = new DictionaryEntry();
            entry.setKey(key);
            entry.setValue(value);
            entry.setLanguageCode(langCode);
            return entry;
        }
    }
}
