package com.sm.instagram.platform.unit.service;

import com.sm.instagram.platform.common.exceptions.ResourceNotFoundException;
import com.sm.instagram.platform.legal.*;
import com.sm.instagram.platform.legal.dto.LegalDocumentDtoOut;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("LegalDocumentService Unit Tests")
class LegalDocumentServiceUnitTest {

    @Mock
    private LegalDocumentRepository repository;

    private LegalDocumentService service;

    @BeforeEach
    void setUp() {
        service = new LegalDocumentService(repository);
    }

    private LegalDocument createDocument(LegalDocumentType type, String language, int version) {
        LegalDocument doc = new LegalDocument();
        doc.setId((long) (type.ordinal() * 100 + version));
        doc.setType(type);
        doc.setLanguage(language);
        doc.setVersion(version);
        doc.setContentHash("sha256-hash-" + type.name().toLowerCase());
        doc.setDocumentUrl("https://storage.example.com/" + type.name().toLowerCase() + "_v" + version + "_" + language + ".pdf");
        doc.setPublishedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        return doc;
    }

    @Nested
    @DisplayName("getCurrentDocuments")
    class GetCurrentDocuments {

        @Test
        @DisplayName("should return mapped DTOs")
        void should_return_mapped_dtos() {
            LegalDocument doc1 = createDocument(LegalDocumentType.TERMS_OF_SERVICE, "pl", 1);
            LegalDocument doc2 = createDocument(LegalDocumentType.PRIVACY_POLICY, "en", 1);
            when(repository.findAllCurrentDocuments()).thenReturn(List.of(doc1, doc2));

            List<LegalDocumentDtoOut> result = service.getCurrentDocuments();

            assertThat(result).hasSize(2);
            assertThat(result.get(0).getType()).isEqualTo("TERMS_OF_SERVICE");
            assertThat(result.get(0).getVersion()).isEqualTo(1);
            assertThat(result.get(0).getContentHash()).isEqualTo("sha256-hash-terms_of_service");
            assertThat(result.get(1).getType()).isEqualTo("PRIVACY_POLICY");
        }

        @Test
        @DisplayName("should return empty list when no documents")
        void should_return_empty_list_when_no_documents() {
            when(repository.findAllCurrentDocuments()).thenReturn(Collections.emptyList());

            List<LegalDocumentDtoOut> result = service.getCurrentDocuments();

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("getLatestPublishedAt")
    class GetLatestPublishedAt {

        @Test
        @DisplayName("should return timestamp from repository")
        void should_return_timestamp_from_repository() {
            LocalDateTime expected = LocalDateTime.of(2026, 3, 1, 12, 0);
            when(repository.findLatestPublishedAt()).thenReturn(Optional.of(expected));

            Optional<LocalDateTime> result = service.getLatestPublishedAt();

            assertThat(result).contains(expected);
        }

        @Test
        @DisplayName("should return empty when no documents")
        void should_return_empty_when_no_documents() {
            when(repository.findLatestPublishedAt()).thenReturn(Optional.empty());

            Optional<LocalDateTime> result = service.getLatestPublishedAt();

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("getLatestVersion")
    class GetLatestVersion {

        @Test
        @DisplayName("should return version from repository")
        void should_return_version_from_repository() {
            when(repository.findLatestVersionByType(LegalDocumentType.TERMS_OF_SERVICE))
                    .thenReturn(Optional.of(3));

            Optional<Integer> result = service.getLatestVersion(LegalDocumentType.TERMS_OF_SERVICE);

            assertThat(result).contains(3);
        }

        @Test
        @DisplayName("should return empty when no documents")
        void should_return_empty_when_no_documents() {
            when(repository.findLatestVersionByType(LegalDocumentType.COOKIE_POLICY))
                    .thenReturn(Optional.empty());

            Optional<Integer> result = service.getLatestVersion(LegalDocumentType.COOKIE_POLICY);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByTypeAndLanguageAndVersion")
    class FindByTypeAndLanguageAndVersion {

        @Test
        @DisplayName("should return document when found")
        void should_return_document_when_found() {
            LegalDocument doc = createDocument(LegalDocumentType.TERMS_OF_SERVICE, "pl", 1);
            when(repository.findByTypeAndLanguageAndVersion(LegalDocumentType.TERMS_OF_SERVICE, "pl", 1))
                    .thenReturn(Optional.of(doc));

            LegalDocument result = service.findByTypeAndLanguageAndVersion(
                    LegalDocumentType.TERMS_OF_SERVICE, "pl", 1);

            assertThat(result).isEqualTo(doc);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when not found")
        void should_throw_ResourceNotFoundException_when_not_found() {
            when(repository.findByTypeAndLanguageAndVersion(LegalDocumentType.TERMS_OF_SERVICE, "pl", 99))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.findByTypeAndLanguageAndVersion(
                    LegalDocumentType.TERMS_OF_SERVICE, "pl", 99))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("findLatest")
    class FindLatest {

        @Test
        @DisplayName("should return latest document")
        void should_return_latest_document() {
            LegalDocument doc = createDocument(LegalDocumentType.PRIVACY_POLICY, "en", 2);
            when(repository.findTopByTypeAndLanguageOrderByVersionDesc(LegalDocumentType.PRIVACY_POLICY, "en"))
                    .thenReturn(Optional.of(doc));

            LegalDocument result = service.findLatest(LegalDocumentType.PRIVACY_POLICY, "en");

            assertThat(result).isEqualTo(doc);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when not found")
        void should_throw_ResourceNotFoundException_when_not_found() {
            when(repository.findTopByTypeAndLanguageOrderByVersionDesc(LegalDocumentType.PRIVACY_POLICY, "de"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.findLatest(LegalDocumentType.PRIVACY_POLICY, "de"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("findByDocumentName")
    class FindByDocumentName {

        @Test
        @DisplayName("should parse terms_of_service_v1_pl.pdf")
        void should_parse_terms_of_service_v1_pl_pdf() {
            LegalDocument doc = createDocument(LegalDocumentType.TERMS_OF_SERVICE, "pl", 1);
            when(repository.findByTypeAndLanguageAndVersion(LegalDocumentType.TERMS_OF_SERVICE, "pl", 1))
                    .thenReturn(Optional.of(doc));

            LegalDocument result = service.findByDocumentName("terms_of_service_v1_pl.pdf");

            assertThat(result).isEqualTo(doc);
        }

        @Test
        @DisplayName("should parse cookie_policy_v2_en.pdf")
        void should_parse_cookie_policy_v2_en_pdf() {
            LegalDocument doc = createDocument(LegalDocumentType.COOKIE_POLICY, "en", 2);
            when(repository.findByTypeAndLanguageAndVersion(LegalDocumentType.COOKIE_POLICY, "en", 2))
                    .thenReturn(Optional.of(doc));

            LegalDocument result = service.findByDocumentName("cookie_policy_v2_en.pdf");

            assertThat(result).isEqualTo(doc);
        }

        @Test
        @DisplayName("should parse privacy_policy_v1_en.pdf")
        void should_parse_privacy_policy_v1_en_pdf() {
            LegalDocument doc = createDocument(LegalDocumentType.PRIVACY_POLICY, "en", 1);
            when(repository.findByTypeAndLanguageAndVersion(LegalDocumentType.PRIVACY_POLICY, "en", 1))
                    .thenReturn(Optional.of(doc));

            LegalDocument result = service.findByDocumentName("privacy_policy_v1_en.pdf");

            assertThat(result).isEqualTo(doc);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException for name with no underscore")
        void should_throw_ResourceNotFoundException_for_no_underscore() {
            assertThatThrownBy(() -> service.findByDocumentName("nodashes.pdf"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException for no version marker")
        void should_throw_ResourceNotFoundException_for_no_version_marker() {
            assertThatThrownBy(() -> service.findByDocumentName("terms_of_service_pl.pdf"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException for invalid type")
        void should_throw_ResourceNotFoundException_for_invalid_type() {
            assertThatThrownBy(() -> service.findByDocumentName("invalid_type_v1_pl.pdf"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("findById")
    class FindById {

        @Test
        @DisplayName("should return document when found")
        void should_return_document_when_found() {
            LegalDocument doc = createDocument(LegalDocumentType.TERMS_OF_SERVICE, "pl", 1);
            when(repository.findById(100L)).thenReturn(Optional.of(doc));

            LegalDocument result = service.findById(100L);

            assertThat(result).isEqualTo(doc);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when not found")
        void should_throw_ResourceNotFoundException_when_not_found() {
            when(repository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.findById(999L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }
}
