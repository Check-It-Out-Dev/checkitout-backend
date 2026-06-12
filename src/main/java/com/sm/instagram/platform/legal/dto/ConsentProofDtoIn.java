package com.sm.instagram.platform.legal.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ConsentProofDtoIn {
    private Boolean eventTrusted;
    private Long timestamp;
    private Double screenX;
    private Double screenY;
    private String checkboxId;
    private String documentHash;
}
