package com.sm.instagram.platform.legal;

import com.sm.instagram.platform.common.exceptions.ResourceNotFoundException;
import com.sm.instagram.platform.legal.dto.LegalDocumentDtoOut;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class LegalDocumentService {

    private final LegalDocumentRepository repository;

    /**
     * Get current (latest version) documents for all types and languages.
     * Used by GET /legal/current endpoint.
     */
    public List<LegalDocumentDtoOut> getCurrentDocuments() {
        return repository.findAllCurrentDocuments().stream()
                .map(this::toDto)
                .toList();
    }

    /**
     * Get the most recent published_at timestamp across all documents.
     * Used to compute the 38-day grace period start.
     */
    public Optional<LocalDateTime> getLatestPublishedAt() {
        return repository.findLatestPublishedAt();
    }

    /**
     * Get the latest version number for a specific document type.
     */
    public Optional<Integer> getLatestVersion(LegalDocumentType type) {
        return repository.findLatestVersionByType(type);
    }

    /**
     * Find a specific document by type, language, and version.
     */
    public LegalDocument findByTypeAndLanguageAndVersion(LegalDocumentType type, String language, Integer version) {
        return repository.findByTypeAndLanguageAndVersion(type, language, version)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "error.legal.document_not_found",
                        type.name(), language, String.valueOf(version)));
    }

    /**
     * Find the latest document for a given type and language.
     */
    public LegalDocument findLatest(LegalDocumentType type, String language) {
        return repository.findTopByTypeAndLanguageOrderByVersionDesc(type, language)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "error.legal.document_not_found", type.name(), language));
    }

    /**
     * Parse a document name like "terms_of_service_v1_pl.pdf" and find the document.
     */
    public LegalDocument findByDocumentName(String documentName) {
        // Expected format: {type}_v{version}_{language}.pdf
        // e.g., "terms_of_service_v1_pl.pdf", "cookie_policy_v2_en.pdf"
        String name = documentName.replace(".pdf", "");

        // Extract language (last segment after underscore)
        int lastUnderscore = name.lastIndexOf('_');
        if (lastUnderscore < 0) {
            throw new ResourceNotFoundException("error.legal.invalid_document_name", documentName);
        }
        String language = name.substring(lastUnderscore + 1);

        // Extract version (segment before language, after _v)
        String beforeLanguage = name.substring(0, lastUnderscore);
        int vIdx = beforeLanguage.lastIndexOf("_v");
        if (vIdx < 0) {
            throw new ResourceNotFoundException("error.legal.invalid_document_name", documentName);
        }
        int version;
        try {
            version = Integer.parseInt(beforeLanguage.substring(vIdx + 2));
        } catch (NumberFormatException e) {
            throw new ResourceNotFoundException("error.legal.invalid_document_name", documentName);
        }

        // Extract type (everything before _v)
        String typeStr = beforeLanguage.substring(0, vIdx).toUpperCase();
        LegalDocumentType type;
        try {
            type = LegalDocumentType.valueOf(typeStr);
        } catch (IllegalArgumentException e) {
            throw new ResourceNotFoundException("error.legal.invalid_document_name", documentName);
        }

        return findByTypeAndLanguageAndVersion(type, language, version);
    }

    public LegalDocument findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("error.legal.document_not_found", String.valueOf(id)));
    }

    private LegalDocumentDtoOut toDto(LegalDocument doc) {
        return LegalDocumentDtoOut.builder()
                .type(doc.getType().name())
                .version(doc.getVersion())
                .contentHash(doc.getContentHash())
                .downloadUrl(doc.getDocumentUrl())
                .effectiveFrom(doc.getPublishedAt().toString())
                .build();
    }
}
