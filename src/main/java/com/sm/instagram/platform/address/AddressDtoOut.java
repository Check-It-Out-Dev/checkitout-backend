package com.sm.instagram.platform.address;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Data Transfer Object for sending Address data in API responses.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AddressDtoOut {

    private Long id;
    private Long userId;
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
    private String updaterId;
    private boolean copied;
    private Long sourceAddressId;
    private Long partnershipOpportunityId;
}