package com.sm.instagram.platform.legal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

/**
 * JSONB payload stored in consent_record.consent_proof.
 * Contains the full proof bundle proving the user actively consented.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsentProofPayload implements Serializable {

    private static final long serialVersionUID = 1L;

    private String timestamp;
    private Boolean isTrusted;
    private String documentHash;
    private String userAgent;
    private String language;
    private String documentName;
    private Double screenX;
    private Double screenY;
    private String checkboxId;
    private Long consentRecordId;
    private Map<String, Boolean> categories;
}
