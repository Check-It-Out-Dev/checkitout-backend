package com.sm.instagram.platform.user;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.sm.instagram.platform.address.AddressNoUserDtoOut;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public non-sealed class CompanyPublicProfileDto implements PublicProfileDto {
    private Long id;
    private String name;
    private String profilePicture;
    private List<AddressNoUserDtoOut> addresses;
    private String description;
    private AccountStatusDtoOut accountStatus;
    private String website;
    private Boolean premium;
    private String email;
    private String phoneNumber;
}
