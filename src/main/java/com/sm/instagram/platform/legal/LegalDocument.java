package com.sm.instagram.platform.legal;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "legal_document",
        uniqueConstraints = @UniqueConstraint(columnNames = {"type", "language", "version"}))
public class LegalDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "legal_document_generator")
    @SequenceGenerator(
            name = "legal_document_generator",
            sequenceName = "legal_document_seq",
            schema = "public",
            allocationSize = 50,
            initialValue = 1
    )
    private Long id;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 50)
    private LegalDocumentType type;

    @NotBlank
    @Column(name = "language", nullable = false, length = 5)
    private String language;

    @NotNull
    @Column(name = "version", nullable = false)
    private Integer version;

    @NotBlank
    @Column(name = "content_hash", nullable = false, length = 128)
    private String contentHash;

    @NotBlank
    @Column(name = "document_url", nullable = false, columnDefinition = "TEXT")
    private String documentUrl;

    @NotNull
    @Column(name = "published_at", nullable = false)
    private LocalDateTime publishedAt;
}
