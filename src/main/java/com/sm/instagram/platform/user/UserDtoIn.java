package com.sm.instagram.platform.user;

import com.sm.instagram.platform.address.AddressDtoIn;
import com.sm.instagram.platform.common.util.ValidationPatterns;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class UserDtoIn {
    private Long id;
    @Size(max = 255, message = "{validation.user.firebaseUserId.size}")
    @Pattern(regexp = "^[a-zA-Z0-9_\\-\\.]+$", message = "{validation.user.firebaseUserId.pattern}")
    private String firebaseUserId;
    @NotNull(message = "{validation.user.userType.required}")
    private UserType userType;
    @NotBlank(message = "{validation.email.required}")
    @Email(message = "{validation.email.format}")
    @Size(max = 255, message = "{validation.email.size}")
    private String email;
    @Size(min = 2, max = 50, message = "{validation.user.firstName.size}")
    private String firstName;
    @Size(min = 2, max = 50, message = "{validation.user.lastName.size}")
    private String lastName;
    private String name;
    @Pattern(
            regexp = ValidationPatterns.HTTPS_URL_PATTERN,
            message = "URL must use HTTPS protocol and be properly formatted"
    )
    @Size(max = 2048, message = "URL cannot exceed 2048 characters")
    private String profilePicture;
    private List<AddressDtoIn> addresses;
    private List<Long> addressesIds;
    @Pattern(regexp = "^\\+?[0-9()-]{7,25}$", message = "{validation.phoneNumber.pattern}")
    @Size(max = 25, message = "{validation.phoneNumber.size}")
    private String phoneNumber;
    @Size(max = 1000, message = "{validation.user.noteFromAdmin.size}")
    private String noteFromAdmin;  // CIO-341: No default - set by UserController via DefaultNoteService
    @Schema(description = "Account status. Valid transitions: "
            + "INACTIVE -> IN_VALIDATION, TO_BE_DELETED, BANNED; "
            + "IN_VALIDATION -> ACTIVE, INACTIVE, TO_BE_DELETED, BANNED; "
            + "ACTIVE -> INACTIVE, TO_BE_DELETED, BANNED; "
            + "BANNED -> ACTIVE, TO_BE_DELETED; "
            + "TO_BE_DELETED -> DELETED, ACTIVE, IN_VALIDATION; "
            + "DELETED -> (terminal, no transitions)")
    @NotNull(message = "{validation.user.accountStatus.required}")
    private AccountStatus accountStatus;

}
