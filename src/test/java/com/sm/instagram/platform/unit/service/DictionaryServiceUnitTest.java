package com.sm.instagram.platform.unit.service;

import com.sm.instagram.platform.dictionary.DictionaryEntry;
import com.sm.instagram.platform.dictionary.DictionaryEntryRepository;
import com.sm.instagram.platform.dictionary.DictionaryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DictionaryService.
 * Uses pure Mockito - no Spring context loaded.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DictionaryService Unit Tests")
class DictionaryServiceUnitTest {

    @Mock
    private DictionaryEntryRepository repository;

    @InjectMocks
    private DictionaryService service;

    private DictionaryEntry testEntry;
    private UUID testId;

    @BeforeEach
    void setUp() {
        testId = UUID.randomUUID();
        testEntry = new DictionaryEntry();
        testEntry.setId(testId);
        testEntry.setKey("test.key");
        testEntry.setValue("Test Value");
        testEntry.setLanguageCode("en");
        testEntry.setCategory("TEST_CATEGORY");
    }

    @Nested
    @DisplayName("getTranslation")
    class GetTranslationTests {

        @Test
        @DisplayName("should return translation when found")
        void shouldReturnTranslationWhenFound() {
            // Given
            when(repository.findByKeyAndLanguageCode("test.key", "en"))
                    .thenReturn(Optional.of(testEntry));

            // When
            Optional<String> result = service.getTranslation("test.key", "en");

            // Then
            assertThat(result).isPresent();
            assertThat(result.get()).isEqualTo("Test Value");
            verify(repository).findByKeyAndLanguageCode("test.key", "en");
        }

        @Test
        @DisplayName("should return empty when translation not found")
        void shouldReturnEmptyWhenNotFound() {
            // Given
            when(repository.findByKeyAndLanguageCode("unknown.key", "en"))
                    .thenReturn(Optional.empty());

            // When
            Optional<String> result = service.getTranslation("unknown.key", "en");

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should search with correct key and language")
        void shouldSearchWithCorrectKeyAndLanguage() {
            // Given
            when(repository.findByKeyAndLanguageCode(any(), any()))
                    .thenReturn(Optional.empty());

            // When
            service.getTranslation("my.key", "pl");

            // Then
            verify(repository).findByKeyAndLanguageCode("my.key", "pl");
        }

        @Test
        @DisplayName("should handle Polish translations")
        void shouldHandlePolishTranslations() {
            // Given
            DictionaryEntry polishEntry = new DictionaryEntry();
            polishEntry.setKey("greeting");
            polishEntry.setValue("Cześć");
            polishEntry.setLanguageCode("pl");
            when(repository.findByKeyAndLanguageCode("greeting", "pl"))
                    .thenReturn(Optional.of(polishEntry));

            // When
            Optional<String> result = service.getTranslation("greeting", "pl");

            // Then
            assertThat(result).isPresent();
            assertThat(result.get()).isEqualTo("Cześć");
        }
    }

    @Nested
    @DisplayName("getEntriesByCategory")
    class GetEntriesByCategoryTests {

        @Test
        @DisplayName("should return entries for category")
        void shouldReturnEntriesForCategory() {
            // Given
            List<DictionaryEntry> entries = List.of(testEntry);
            when(repository.findByCategory("TEST_CATEGORY")).thenReturn(entries);

            // When
            List<DictionaryEntry> result = service.getEntriesByCategory("TEST_CATEGORY");

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getKey()).isEqualTo("test.key");
        }

        @Test
        @DisplayName("should return empty list when no entries in category")
        void shouldReturnEmptyListWhenNoCategoryEntries() {
            // Given
            when(repository.findByCategory("EMPTY_CATEGORY")).thenReturn(Collections.emptyList());

            // When
            List<DictionaryEntry> result = service.getEntriesByCategory("EMPTY_CATEGORY");

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should return multiple entries for category")
        void shouldReturnMultipleEntriesForCategory() {
            // Given
            DictionaryEntry entry1 = createEntry("key1", "value1", "en");
            DictionaryEntry entry2 = createEntry("key2", "value2", "en");
            DictionaryEntry entry3 = createEntry("key3", "value3", "pl");
            when(repository.findByCategory("MULTI")).thenReturn(List.of(entry1, entry2, entry3));

            // When
            List<DictionaryEntry> result = service.getEntriesByCategory("MULTI");

            // Then
            assertThat(result).hasSize(3);
        }
    }

    @Nested
    @DisplayName("createOrUpdateEntry")
    class CreateOrUpdateEntryTests {

        @Test
        @DisplayName("should save new entry")
        void shouldSaveNewEntry() {
            // Given
            DictionaryEntry newEntry = createEntry("new.key", "New Value", "en");
            when(repository.save(any(DictionaryEntry.class))).thenReturn(newEntry);

            // When
            DictionaryEntry result = service.createOrUpdateEntry(newEntry);

            // Then
            assertThat(result.getKey()).isEqualTo("new.key");
            assertThat(result.getValue()).isEqualTo("New Value");
            verify(repository).save(newEntry);
        }

        @Test
        @DisplayName("should update existing entry")
        void shouldUpdateExistingEntry() {
            // Given
            testEntry.setValue("Updated Value");
            when(repository.save(testEntry)).thenReturn(testEntry);

            // When
            DictionaryEntry result = service.createOrUpdateEntry(testEntry);

            // Then
            assertThat(result.getValue()).isEqualTo("Updated Value");
            verify(repository).save(testEntry);
        }

        @Test
        @DisplayName("should preserve entry category")
        void shouldPreserveEntryCategory() {
            // Given
            DictionaryEntry entryWithCategory = createEntry("cat.key", "Cat Value", "en");
            entryWithCategory.setCategory("SPECIAL_CATEGORY");
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // When
            DictionaryEntry result = service.createOrUpdateEntry(entryWithCategory);

            // Then
            assertThat(result.getCategory()).isEqualTo("SPECIAL_CATEGORY");
        }
    }

    @Nested
    @DisplayName("deleteEntry")
    class DeleteEntryTests {

        @Test
        @DisplayName("should delete entry by ID")
        void shouldDeleteEntryById() {
            // Given
            doNothing().when(repository).deleteById(testId);

            // When
            service.deleteEntry(testId);

            // Then
            verify(repository).deleteById(testId);
        }

        @Test
        @DisplayName("should call repository delete exactly once")
        void shouldCallRepositoryDeleteOnce() {
            // Given
            UUID id = UUID.randomUUID();
            doNothing().when(repository).deleteById(id);

            // When
            service.deleteEntry(id);

            // Then
            verify(repository, times(1)).deleteById(id);
        }
    }

    @Nested
    @DisplayName("getEntriesByLanguage")
    class GetEntriesByLanguageTests {

        @Test
        @DisplayName("should return entries for English")
        void shouldReturnEntriesForEnglish() {
            // Given
            List<DictionaryEntry> englishEntries = List.of(
                    createEntry("key1", "Value 1", "en"),
                    createEntry("key2", "Value 2", "en")
            );
            when(repository.findByLanguageCode("en")).thenReturn(englishEntries);

            // When
            List<DictionaryEntry> result = service.getEntriesByLanguage("en");

            // Then
            assertThat(result).hasSize(2);
            assertThat(result).allMatch(e -> "en".equals(e.getLanguageCode()));
        }

        @Test
        @DisplayName("should return entries for Polish")
        void shouldReturnEntriesForPolish() {
            // Given
            List<DictionaryEntry> polishEntries = List.of(
                    createEntry("powitanie", "Cześć", "pl"),
                    createEntry("pożegnanie", "Do widzenia", "pl")
            );
            when(repository.findByLanguageCode("pl")).thenReturn(polishEntries);

            // When
            List<DictionaryEntry> result = service.getEntriesByLanguage("pl");

            // Then
            assertThat(result).hasSize(2);
            assertThat(result).allMatch(e -> "pl".equals(e.getLanguageCode()));
        }

        @Test
        @DisplayName("should return empty list for unsupported language")
        void shouldReturnEmptyForUnsupportedLanguage() {
            // Given
            when(repository.findByLanguageCode("xx")).thenReturn(Collections.emptyList());

            // When
            List<DictionaryEntry> result = service.getEntriesByLanguage("xx");

            // Then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("findAll")
    class FindAllTests {

        @Test
        @DisplayName("should return all entries")
        void shouldReturnAllEntries() {
            // Given
            List<DictionaryEntry> allEntries = List.of(
                    createEntry("key1", "Value 1", "en"),
                    createEntry("key2", "Value 2", "en"),
                    createEntry("key3", "Wartość 3", "pl")
            );
            when(repository.findAll()).thenReturn(allEntries);

            // When
            List<DictionaryEntry> result = service.findAll();

            // Then
            assertThat(result).hasSize(3);
        }

        @Test
        @DisplayName("should return empty list when no entries")
        void shouldReturnEmptyListWhenNoEntries() {
            // Given
            when(repository.findAll()).thenReturn(Collections.emptyList());

            // When
            List<DictionaryEntry> result = service.findAll();

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should call repository findAll")
        void shouldCallRepositoryFindAll() {
            // Given
            when(repository.findAll()).thenReturn(Collections.emptyList());

            // When
            service.findAll();

            // Then
            verify(repository).findAll();
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("should handle null key gracefully")
        void shouldHandleNullKey() {
            // Given
            when(repository.findByKeyAndLanguageCode(null, "en"))
                    .thenReturn(Optional.empty());

            // When
            Optional<String> result = service.getTranslation(null, "en");

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should handle empty key")
        void shouldHandleEmptyKey() {
            // Given
            when(repository.findByKeyAndLanguageCode("", "en"))
                    .thenReturn(Optional.empty());

            // When
            Optional<String> result = service.getTranslation("", "en");

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should handle special characters in key")
        void shouldHandleSpecialCharactersInKey() {
            // Given
            DictionaryEntry specialEntry = createEntry("key.with.dots.and-dashes_underscores", "Special", "en");
            when(repository.findByKeyAndLanguageCode("key.with.dots.and-dashes_underscores", "en"))
                    .thenReturn(Optional.of(specialEntry));

            // When
            Optional<String> result = service.getTranslation("key.with.dots.and-dashes_underscores", "en");

            // Then
            assertThat(result).isPresent();
            assertThat(result.get()).isEqualTo("Special");
        }

        @Test
        @DisplayName("should handle Unicode values")
        void shouldHandleUnicodeValues() {
            // Given
            DictionaryEntry unicodeEntry = createEntry("emoji", "Hello 👋 World 🌍", "en");
            when(repository.findByKeyAndLanguageCode("emoji", "en"))
                    .thenReturn(Optional.of(unicodeEntry));

            // When
            Optional<String> result = service.getTranslation("emoji", "en");

            // Then
            assertThat(result).isPresent();
            assertThat(result.get()).contains("👋");
        }
    }

    // Helper method
    private DictionaryEntry createEntry(String key, String value, String languageCode) {
        DictionaryEntry entry = new DictionaryEntry();
        entry.setId(UUID.randomUUID());
        entry.setKey(key);
        entry.setValue(value);
        entry.setLanguageCode(languageCode);
        return entry;
    }
}
