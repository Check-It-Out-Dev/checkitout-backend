package com.sm.instagram.platform.user;

import com.sm.instagram.platform.address.AddressCityOnlyDto;
import com.sm.instagram.platform.usersocialconnection.UserSocialConnectionDtoOut;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class InfluencerForCompanyProfileDto extends InfluencerPublicProfileDto {
    // Fields already in parent: id, name, profilePicture, accountStatus
    private UserTypeDtoOut userType;
    private String email;
    private String firstName;
    private String lastName;
    private List<AddressCityOnlyDto> addresses;
    private String phoneNumber;
    private List<UserSocialConnectionDtoOut> socialConnections;
    private Boolean premium;
}
