package com.sm.instagram.platform.consent;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class UserCurrentConsentId implements Serializable {
    private Long userId;
    private Long consentDefinitionId;
}
