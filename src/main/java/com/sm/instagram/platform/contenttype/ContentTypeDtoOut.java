package com.sm.instagram.platform.contenttype;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Outbound DTO for content types served to the frontend.
 *
 * <p>Carries the translated display name in {@code name} (resolved via
 * Transloco against the request locale) and the canonical English value in
 * {@code originalName}. The original is kept alongside the translation so the
 * UI can fall back gracefully when a locale lacks a translation entry.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContentTypeDtoOut {

    private Long id;
    private String name;
    private String originalName;
}
