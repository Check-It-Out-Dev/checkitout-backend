package com.sm.instagram.platform.legal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * HMAC cookie payload for category-level cookie consent (banner toggles).
 * Separate from ConsentProofPayload which handles registration document consent.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryConsentPayload implements Serializable {

    private static final long serialVersionUID = 1L;

    private String categoryType;
    private String timestamp;
    private Boolean isTrusted;
    private String userAgent;
}
