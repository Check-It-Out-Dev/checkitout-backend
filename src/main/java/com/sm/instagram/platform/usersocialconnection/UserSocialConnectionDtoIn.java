package com.sm.instagram.platform.usersocialconnection;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserSocialConnectionDtoIn {
    private Long id;

    private Long userId;

    private Long platform;

    private String socialUserId;

    private String profileUrl;
    private String profilePictureUrl;
    private String displayName;
    private String email;
    private String note;

    private Long serviceType;

    private Integer followersCount;

    private Boolean isPrimary;

    private ConnectionStatus connectionStatus;


}
