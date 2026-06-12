package com.sm.instagram.platform.consent;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ConsentDefinitionDtoOut {
    private Long id;
    private String consentType;
    private String name;
    private String description;
    private String regulationReference;
    private Boolean isActive;
    private List<ConsentVersionDtoOut> versions;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
