package com.sm.instagram.platform.consent;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserConsentDtoOut {
    private Long id;
    private Long userId;
    private ConsentVersionDtoOut consentVersion;
    private ConsentActionDtoOut action;              // Translated ConsentAction
    private Boolean consentGiven;
    private CollectionMethodDtoOut collectionMethod; // Translated Collection Method
    private LegalBasisDtoOut legalBasis;             // Translated Legal Basis
    private LocalDateTime createdAt;
}
