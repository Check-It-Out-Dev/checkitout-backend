package com.sm.instagram.platform.usersocialconnection;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.sm.instagram.platform.platform.PlatformDto;
import com.sm.instagram.platform.servicetype.ServiceTypeDto;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserSocialConnectionDtoOut {
    private Long id;

    private Long userId;

    private PlatformDto platform;

    private String socialUserId;

    private String profileUrl;
    private String profilePictureUrl;
    private String displayName;
    private String email;
    private String note;

    private ServiceTypeDto serviceType;

    private Integer followersCount;

    private Boolean isPrimary;

    private ConnectionStatus connectionStatus;

    private LocalDateTime lastSyncTime;

    private LocalDateTime createdTime = LocalDateTime.now();

    private LocalDateTime lastUpdateTime = LocalDateTime.now();
}
