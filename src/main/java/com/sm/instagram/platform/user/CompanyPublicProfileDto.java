package com.sm.instagram.platform.user;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import com.sm.instagram.platform.address.AddressNoUserDtoOut;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public non-sealed class CompanyPublicProfileDto implements PublicProfileDto {

    /** Which of the two shapes this is; see {@link PublicProfileDto}. */
    @Schema(allowableValues = "COMPANY", requiredMode = Schema.RequiredMode.REQUIRED)
    private final String profileType = "COMPANY";

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
