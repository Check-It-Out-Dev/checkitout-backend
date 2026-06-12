package com.sm.instagram.platform.address;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Data Transfer Object for sending Address data without user information in API responses.
 * Used when addresses are nested within user-specific DTOs where userId would be redundant.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AddressNoUserDtoOut {
    
    private Long id;
    
    private String street;
    
    private String city;
    
    private String postalCode;
    
    private String country;
    
    private String state;
    
    private String additionalInfo;
    
    private String addressType;
    
    private boolean isPrimary;
    
    private LocalDateTime createdTime;
    
    private LocalDateTime lastUpdateTime;

    private AddressSourceType sourceType;

    private boolean isShared = false;
}
