package com.sm.instagram.platform.user;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.sm.instagram.platform.address.AddressNoUserDtoOut;
import com.sm.instagram.platform.usersocialconnection.UserSocialConnectionDtoOut;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserDtoOut {
    private Long id;
    private String firebaseUserId;
    private UserTypeDtoOut userType;
    private String email;
    private String firstName;
    private String lastName;
    private String name;
    /**
     * Sent even when null, so a client clearing its avatar sees the field go
     * to null instead of merging the old value back in — which also makes it
     * the one property of this DTO a consumer can receive as null, so the
     * document has to say so.
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    @Schema(description = "Avatar URL; null when the user has no picture set")
    private String profilePicture;
    private List<AddressNoUserDtoOut> addresses;
    private String phoneNumber;
    private String noteFromAdmin;
    private AccountStatusDtoOut accountStatus;
    private LocalDateTime createdTime;
    private LocalDateTime lastUpdateTime;
    private String updater;
    private List<UserSocialConnectionDtoOut> socialConnections;
    private String companyDescription;
    private String nip;
    private Boolean premium;
    private Boolean emailVerified;  // Email verification status from Firebase
    private String lastVerifiedEmail;  // Last proven email for step-up auth display
    private Boolean profileComplete;
    private List<String> profileMissingFields;
    private Boolean newestConsentsAccepted;
    private Integer daysToAcceptNewTerms;
    private Boolean initialAccountSetupCompleted;
}
