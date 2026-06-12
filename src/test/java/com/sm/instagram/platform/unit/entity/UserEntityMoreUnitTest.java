package com.sm.instagram.platform.unit.entity;

import com.sm.instagram.platform.address.AddressCityOnlyDto;
import com.sm.instagram.platform.address.AddressNoUserDtoOut;
import com.sm.instagram.platform.user.*;
import com.sm.instagram.platform.user.dto.DeletionBlocker;
import com.sm.instagram.platform.user.dto.DeletionBlockerCategory;
import com.sm.instagram.platform.user.dto.DeletionEligibilityDto;
import com.sm.instagram.platform.usersocialconnection.UserSocialConnectionDtoOut;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * Comprehensive unit tests for User entity DTOs, enums, and DefaultNoteService.
 * Complements UserEntityUnitTest and UserPackageUnitTest with additional coverage.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("User Entity More Unit Tests")
class UserEntityMoreUnitTest {

    // ==================== AccountStatusDtoOut Tests ====================

    @Nested
    @DisplayName("AccountStatusDtoOut Tests")
    class AccountStatusDtoOutTests {

        @Nested
        @DisplayName("Builder Tests")
        class BuilderTests {

            @Test
            @DisplayName("should build AccountStatusDtoOut with all fields")
            void shouldBuildAccountStatusDtoOutWithAllFields() {
                AccountStatusDtoOut dto = AccountStatusDtoOut.builder()
                        .value("ACTIVE")
                        .label("Active")
                        .description("Account is active")
                        .originalLabel("ACTIVE")
                        .colorTheme("success")
                        .icon("user-check")
                        .isActive(true)
                        .canLogin(true)
                        .isTerminal(false)
                        .build();

                assertThat(dto.getValue()).isEqualTo("ACTIVE");
                assertThat(dto.getLabel()).isEqualTo("Active");
                assertThat(dto.getDescription()).isEqualTo("Account is active");
                assertThat(dto.getOriginalLabel()).isEqualTo("ACTIVE");
                assertThat(dto.getColorTheme()).isEqualTo("success");
                assertThat(dto.getIcon()).isEqualTo("user-check");
                assertThat(dto.isActive()).isTrue();
                assertThat(dto.isCanLogin()).isTrue();
                assertThat(dto.isTerminal()).isFalse();
            }

            @Test
            @DisplayName("should build AccountStatusDtoOut with minimal fields")
            void shouldBuildAccountStatusDtoOutWithMinimalFields() {
                AccountStatusDtoOut dto = AccountStatusDtoOut.builder()
                        .value("INACTIVE")
                        .build();

                assertThat(dto.getValue()).isEqualTo("INACTIVE");
                assertThat(dto.getLabel()).isNull();
                assertThat(dto.isActive()).isFalse();
            }

            @Test
            @DisplayName("should build with default boolean values as false")
            void shouldBuildWithDefaultBooleanValuesAsFalse() {
                AccountStatusDtoOut dto = AccountStatusDtoOut.builder().build();

                assertThat(dto.isActive()).isFalse();
                assertThat(dto.isCanLogin()).isFalse();
                assertThat(dto.isTerminal()).isFalse();
            }
        }

        @Nested
        @DisplayName("Getter and Setter Tests")
        class GetterSetterTests {

            private AccountStatusDtoOut dto;

            @BeforeEach
            void setUp() {
                dto = new AccountStatusDtoOut();
            }

            @Test
            @DisplayName("should get and set value")
            void shouldGetAndSetValue() {
                dto.setValue("BANNED");
                assertThat(dto.getValue()).isEqualTo("BANNED");
            }

            @Test
            @DisplayName("should get and set label")
            void shouldGetAndSetLabel() {
                dto.setLabel("Zbanowany");
                assertThat(dto.getLabel()).isEqualTo("Zbanowany");
            }

            @Test
            @DisplayName("should get and set description")
            void shouldGetAndSetDescription() {
                dto.setDescription("Account has been banned");
                assertThat(dto.getDescription()).isEqualTo("Account has been banned");
            }

            @Test
            @DisplayName("should get and set originalLabel")
            void shouldGetAndSetOriginalLabel() {
                dto.setOriginalLabel("BANNED");
                assertThat(dto.getOriginalLabel()).isEqualTo("BANNED");
            }

            @Test
            @DisplayName("should get and set colorTheme")
            void shouldGetAndSetColorTheme() {
                dto.setColorTheme("danger");
                assertThat(dto.getColorTheme()).isEqualTo("danger");
            }

            @Test
            @DisplayName("should get and set icon")
            void shouldGetAndSetIcon() {
                dto.setIcon("ban");
                assertThat(dto.getIcon()).isEqualTo("ban");
            }

            @Test
            @DisplayName("should get and set isActive")
            void shouldGetAndSetIsActive() {
                dto.setActive(true);
                assertThat(dto.isActive()).isTrue();
            }

            @Test
            @DisplayName("should get and set canLogin")
            void shouldGetAndSetCanLogin() {
                dto.setCanLogin(true);
                assertThat(dto.isCanLogin()).isTrue();
            }

            @Test
            @DisplayName("should get and set isTerminal")
            void shouldGetAndSetIsTerminal() {
                dto.setTerminal(true);
                assertThat(dto.isTerminal()).isTrue();
            }
        }

        @Nested
        @DisplayName("Equals and HashCode Tests")
        class EqualsHashCodeTests {

            @Test
            @DisplayName("should be equal when all fields match")
            void shouldBeEqualWhenAllFieldsMatch() {
                AccountStatusDtoOut dto1 = AccountStatusDtoOut.builder()
                        .value("ACTIVE")
                        .label("Active")
                        .colorTheme("success")
                        .build();

                AccountStatusDtoOut dto2 = AccountStatusDtoOut.builder()
                        .value("ACTIVE")
                        .label("Active")
                        .colorTheme("success")
                        .build();

                assertThat(dto1).isEqualTo(dto2);
                assertThat(dto1.hashCode()).isEqualTo(dto2.hashCode());
            }

            @Test
            @DisplayName("should not be equal when value differs")
            void shouldNotBeEqualWhenValueDiffers() {
                AccountStatusDtoOut dto1 = AccountStatusDtoOut.builder()
                        .value("ACTIVE")
                        .build();

                AccountStatusDtoOut dto2 = AccountStatusDtoOut.builder()
                        .value("INACTIVE")
                        .build();

                assertThat(dto1).isNotEqualTo(dto2);
            }

            @Test
            @DisplayName("should not be equal to null")
            void shouldNotBeEqualToNull() {
                AccountStatusDtoOut dto = AccountStatusDtoOut.builder().build();
                assertThat(dto).isNotEqualTo(null);
            }

            @Test
            @DisplayName("should be equal to itself")
            void shouldBeEqualToItself() {
                AccountStatusDtoOut dto = AccountStatusDtoOut.builder().value("ACTIVE").build();
                assertThat(dto).isEqualTo(dto);
            }
        }

        @Nested
        @DisplayName("ToString Tests")
        class ToStringTests {

            @Test
            @DisplayName("should include value in toString")
            void shouldIncludeValueInToString() {
                AccountStatusDtoOut dto = AccountStatusDtoOut.builder()
                        .value("ACTIVE")
                        .label("Active")
                        .build();

                String toString = dto.toString();
                assertThat(toString).contains("ACTIVE");
                assertThat(toString).contains("Active");
            }
        }

        @Nested
        @DisplayName("AllArgsConstructor Tests")
        class AllArgsConstructorTests {

            @Test
            @DisplayName("should create with all args constructor")
            void shouldCreateWithAllArgsConstructor() {
                AccountStatusDtoOut dto = new AccountStatusDtoOut(
                        "ACTIVE", "Active", "Account is active", "ACTIVE",
                        "success", "user-check", true, true, false
                );

                assertThat(dto.getValue()).isEqualTo("ACTIVE");
                assertThat(dto.getLabel()).isEqualTo("Active");
                assertThat(dto.getDescription()).isEqualTo("Account is active");
                assertThat(dto.getOriginalLabel()).isEqualTo("ACTIVE");
                assertThat(dto.getColorTheme()).isEqualTo("success");
                assertThat(dto.getIcon()).isEqualTo("user-check");
                assertThat(dto.isActive()).isTrue();
                assertThat(dto.isCanLogin()).isTrue();
                assertThat(dto.isTerminal()).isFalse();
            }
        }

        @Nested
        @DisplayName("NoArgsConstructor Tests")
        class NoArgsConstructorTests {

            @Test
            @DisplayName("should create with no args constructor")
            void shouldCreateWithNoArgsConstructor() {
                AccountStatusDtoOut dto = new AccountStatusDtoOut();

                assertThat(dto).isNotNull();
                assertThat(dto.getValue()).isNull();
                assertThat(dto.getLabel()).isNull();
                assertThat(dto.isActive()).isFalse();
            }
        }
    }

    // ==================== UserTypeDtoOut Tests ====================

    @Nested
    @DisplayName("UserTypeDtoOut Tests")
    class UserTypeDtoOutTests {

        @Nested
        @DisplayName("Builder Tests")
        class BuilderTests {

            @Test
            @DisplayName("should build UserTypeDtoOut with all fields")
            void shouldBuildUserTypeDtoOutWithAllFields() {
                UserTypeDtoOut dto = UserTypeDtoOut.builder()
                        .value("INFLUENCER")
                        .label("Influencer")
                        .originalLabel("INFLUENCER")
                        .build();

                assertThat(dto.getValue()).isEqualTo("INFLUENCER");
                assertThat(dto.getLabel()).isEqualTo("Influencer");
                assertThat(dto.getOriginalLabel()).isEqualTo("INFLUENCER");
            }

            @Test
            @DisplayName("should build UserTypeDtoOut with minimal fields")
            void shouldBuildUserTypeDtoOutWithMinimalFields() {
                UserTypeDtoOut dto = UserTypeDtoOut.builder()
                        .value("COMPANY")
                        .build();

                assertThat(dto.getValue()).isEqualTo("COMPANY");
                assertThat(dto.getLabel()).isNull();
                assertThat(dto.getOriginalLabel()).isNull();
            }
        }

        @Nested
        @DisplayName("Getter and Setter Tests")
        class GetterSetterTests {

            private UserTypeDtoOut dto;

            @BeforeEach
            void setUp() {
                dto = new UserTypeDtoOut();
            }

            @Test
            @DisplayName("should get and set value")
            void shouldGetAndSetValue() {
                dto.setValue("ADMIN");
                assertThat(dto.getValue()).isEqualTo("ADMIN");
            }

            @Test
            @DisplayName("should get and set label")
            void shouldGetAndSetLabel() {
                dto.setLabel("Administrator");
                assertThat(dto.getLabel()).isEqualTo("Administrator");
            }

            @Test
            @DisplayName("should get and set originalLabel")
            void shouldGetAndSetOriginalLabel() {
                dto.setOriginalLabel("ADMIN");
                assertThat(dto.getOriginalLabel()).isEqualTo("ADMIN");
            }
        }

        @Nested
        @DisplayName("Equals and HashCode Tests")
        class EqualsHashCodeTests {

            @Test
            @DisplayName("should be equal when all fields match")
            void shouldBeEqualWhenAllFieldsMatch() {
                UserTypeDtoOut dto1 = UserTypeDtoOut.builder()
                        .value("INFLUENCER")
                        .label("Influencer")
                        .build();

                UserTypeDtoOut dto2 = UserTypeDtoOut.builder()
                        .value("INFLUENCER")
                        .label("Influencer")
                        .build();

                assertThat(dto1).isEqualTo(dto2);
                assertThat(dto1.hashCode()).isEqualTo(dto2.hashCode());
            }

            @Test
            @DisplayName("should not be equal when value differs")
            void shouldNotBeEqualWhenValueDiffers() {
                UserTypeDtoOut dto1 = UserTypeDtoOut.builder().value("ADMIN").build();
                UserTypeDtoOut dto2 = UserTypeDtoOut.builder().value("COMPANY").build();

                assertThat(dto1).isNotEqualTo(dto2);
            }
        }

        @Nested
        @DisplayName("AllArgsConstructor Tests")
        class AllArgsConstructorTests {

            @Test
            @DisplayName("should create with all args constructor")
            void shouldCreateWithAllArgsConstructor() {
                UserTypeDtoOut dto = new UserTypeDtoOut("COMPANY", "Company", "COMPANY");

                assertThat(dto.getValue()).isEqualTo("COMPANY");
                assertThat(dto.getLabel()).isEqualTo("Company");
                assertThat(dto.getOriginalLabel()).isEqualTo("COMPANY");
            }
        }

        @Nested
        @DisplayName("NoArgsConstructor Tests")
        class NoArgsConstructorTests {

            @Test
            @DisplayName("should create with no args constructor")
            void shouldCreateWithNoArgsConstructor() {
                UserTypeDtoOut dto = new UserTypeDtoOut();

                assertThat(dto).isNotNull();
                assertThat(dto.getValue()).isNull();
            }
        }
    }

    // ==================== CompanyPublicProfileDto Tests ====================

    @Nested
    @DisplayName("CompanyPublicProfileDto Tests")
    class CompanyPublicProfileDtoTests {

        private CompanyPublicProfileDto dto;

        @BeforeEach
        void setUp() {
            dto = new CompanyPublicProfileDto();
        }

        @Nested
        @DisplayName("Getter and Setter Tests")
        class GetterSetterTests {

            @Test
            @DisplayName("should get and set id")
            void shouldGetAndSetId() {
                dto.setId(123L);
                assertThat(dto.getId()).isEqualTo(123L);
            }

            @Test
            @DisplayName("should get and set name")
            void shouldGetAndSetName() {
                dto.setName("Test Company");
                assertThat(dto.getName()).isEqualTo("Test Company");
            }

            @Test
            @DisplayName("should get and set profilePicture")
            void shouldGetAndSetProfilePicture() {
                dto.setProfilePicture("https://example.com/logo.png");
                assertThat(dto.getProfilePicture()).isEqualTo("https://example.com/logo.png");
            }

            @Test
            @DisplayName("should get and set addresses")
            void shouldGetAndSetAddresses() {
                List<AddressNoUserDtoOut> addresses = new ArrayList<>();
                AddressNoUserDtoOut address = new AddressNoUserDtoOut();
                addresses.add(address);

                dto.setAddresses(addresses);
                assertThat(dto.getAddresses()).hasSize(1);
            }

            @Test
            @DisplayName("should get and set description")
            void shouldGetAndSetDescription() {
                dto.setDescription("A great company");
                assertThat(dto.getDescription()).isEqualTo("A great company");
            }

            @Test
            @DisplayName("should get and set accountStatus")
            void shouldGetAndSetAccountStatus() {
                AccountStatusDtoOut status = AccountStatusDtoOut.builder()
                        .value("ACTIVE")
                        .build();
                dto.setAccountStatus(status);
                assertThat(dto.getAccountStatus().getValue()).isEqualTo("ACTIVE");
            }

            @Test
            @DisplayName("should get and set website")
            void shouldGetAndSetWebsite() {
                dto.setWebsite("https://company.example.com");
                assertThat(dto.getWebsite()).isEqualTo("https://company.example.com");
            }

            @Test
            @DisplayName("should get and set premium")
            void shouldGetAndSetPremium() {
                dto.setPremium(true);
                assertThat(dto.getPremium()).isTrue();
            }
        }

        @Test
        @DisplayName("should implement PublicProfileDto sealed interface")
        void shouldImplementPublicProfileDtoInterface() {
            assertThat(dto).isInstanceOf(PublicProfileDto.class);
        }

        @Test
        @DisplayName("should have null values by default")
        void shouldHaveNullValuesByDefault() {
            CompanyPublicProfileDto newDto = new CompanyPublicProfileDto();

            assertThat(newDto.getId()).isNull();
            assertThat(newDto.getName()).isNull();
            assertThat(newDto.getProfilePicture()).isNull();
            assertThat(newDto.getAddresses()).isNull();
            assertThat(newDto.getDescription()).isNull();
            assertThat(newDto.getAccountStatus()).isNull();
            assertThat(newDto.getWebsite()).isNull();
            assertThat(newDto.getPremium()).isNull();
        }
    }

    // ==================== InfluencerPublicProfileDto Tests ====================

    @Nested
    @DisplayName("InfluencerPublicProfileDto Tests")
    class InfluencerPublicProfileDtoTests {

        private InfluencerPublicProfileDto dto;

        @BeforeEach
        void setUp() {
            dto = new InfluencerPublicProfileDto();
        }

        @Nested
        @DisplayName("Getter and Setter Tests")
        class GetterSetterTests {

            @Test
            @DisplayName("should get and set id")
            void shouldGetAndSetId() {
                dto.setId(456L);
                assertThat(dto.getId()).isEqualTo(456L);
            }

            @Test
            @DisplayName("should get and set name")
            void shouldGetAndSetName() {
                dto.setName("Influencer Name");
                assertThat(dto.getName()).isEqualTo("Influencer Name");
            }

            @Test
            @DisplayName("should get and set profilePicture")
            void shouldGetAndSetProfilePicture() {
                dto.setProfilePicture("https://example.com/profile.jpg");
                assertThat(dto.getProfilePicture()).isEqualTo("https://example.com/profile.jpg");
            }

            @Test
            @DisplayName("should get and set createdTime")
            void shouldGetAndSetCreatedTime() {
                LocalDateTime now = LocalDateTime.now();
                dto.setCreatedTime(now);
                assertThat(dto.getCreatedTime()).isEqualTo(now);
            }

            @Test
            @DisplayName("should get and set platformName")
            void shouldGetAndSetPlatformName() {
                dto.setPlatformName("Instagram");
                assertThat(dto.getPlatformName()).isEqualTo("Instagram");
            }

            @Test
            @DisplayName("should get and set displayName")
            void shouldGetAndSetDisplayName() {
                dto.setDisplayName("@influencer");
                assertThat(dto.getDisplayName()).isEqualTo("@influencer");
            }

            @Test
            @DisplayName("should get and set profileUrl")
            void shouldGetAndSetProfileUrl() {
                dto.setProfileUrl("https://instagram.com/influencer");
                assertThat(dto.getProfileUrl()).isEqualTo("https://instagram.com/influencer");
            }

            @Test
            @DisplayName("should get and set accountStatus")
            void shouldGetAndSetAccountStatus() {
                AccountStatusDtoOut status = AccountStatusDtoOut.builder()
                        .value("IN_VALIDATION")
                        .build();
                dto.setAccountStatus(status);
                assertThat(dto.getAccountStatus().getValue()).isEqualTo("IN_VALIDATION");
            }

            @Test
            @DisplayName("should get and set followersCount")
            void shouldGetAndSetFollowersCount() {
                dto.setFollowersCount(10000);
                assertThat(dto.getFollowersCount()).isEqualTo(10000);
            }

            @Test
            @DisplayName("should get and set premium")
            void shouldGetAndSetPremium() {
                dto.setPremium(false);
                assertThat(dto.getPremium()).isFalse();
            }
        }

        @Test
        @DisplayName("should implement PublicProfileDto sealed interface")
        void shouldImplementPublicProfileDtoInterface() {
            assertThat(dto).isInstanceOf(PublicProfileDto.class);
        }

        @Test
        @DisplayName("should have null values by default")
        void shouldHaveNullValuesByDefault() {
            InfluencerPublicProfileDto newDto = new InfluencerPublicProfileDto();

            assertThat(newDto.getId()).isNull();
            assertThat(newDto.getName()).isNull();
            assertThat(newDto.getProfilePicture()).isNull();
            assertThat(newDto.getCreatedTime()).isNull();
            assertThat(newDto.getPlatformName()).isNull();
            assertThat(newDto.getDisplayName()).isNull();
            assertThat(newDto.getProfileUrl()).isNull();
            assertThat(newDto.getAccountStatus()).isNull();
            assertThat(newDto.getFollowersCount()).isNull();
            assertThat(newDto.getPremium()).isNull();
        }
    }

    // ==================== InfluencerForCompanyProfileDto Tests ====================

    @Nested
    @DisplayName("InfluencerForCompanyProfileDto Tests")
    class InfluencerForCompanyProfileDtoTests {

        private InfluencerForCompanyProfileDto dto;

        @BeforeEach
        void setUp() {
            dto = new InfluencerForCompanyProfileDto();
        }

        @Test
        @DisplayName("should extend InfluencerPublicProfileDto")
        void shouldExtendInfluencerPublicProfileDto() {
            assertThat(dto).isInstanceOf(InfluencerPublicProfileDto.class);
        }

        @Nested
        @DisplayName("Additional Getter and Setter Tests")
        class AdditionalGetterSetterTests {

            @Test
            @DisplayName("should get and set userType")
            void shouldGetAndSetUserType() {
                UserTypeDtoOut userType = UserTypeDtoOut.builder()
                        .value("INFLUENCER")
                        .label("Influencer")
                        .build();
                dto.setUserType(userType);
                assertThat(dto.getUserType().getValue()).isEqualTo("INFLUENCER");
            }

            @Test
            @DisplayName("should get and set email")
            void shouldGetAndSetEmail() {
                dto.setEmail("influencer@example.com");
                assertThat(dto.getEmail()).isEqualTo("influencer@example.com");
            }

            @Test
            @DisplayName("should get and set firstName")
            void shouldGetAndSetFirstName() {
                dto.setFirstName("John");
                assertThat(dto.getFirstName()).isEqualTo("John");
            }

            @Test
            @DisplayName("should get and set lastName")
            void shouldGetAndSetLastName() {
                dto.setLastName("Doe");
                assertThat(dto.getLastName()).isEqualTo("Doe");
            }

            @Test
            @DisplayName("should get and set addresses")
            void shouldGetAndSetAddresses() {
                List<AddressCityOnlyDto> addresses = new ArrayList<>();
                AddressCityOnlyDto address = new AddressCityOnlyDto();
                addresses.add(address);

                dto.setAddresses(addresses);
                assertThat(dto.getAddresses()).hasSize(1);
            }

            @Test
            @DisplayName("should get and set phoneNumber")
            void shouldGetAndSetPhoneNumber() {
                dto.setPhoneNumber("+48123456789");
                assertThat(dto.getPhoneNumber()).isEqualTo("+48123456789");
            }

            @Test
            @DisplayName("should get and set socialConnections")
            void shouldGetAndSetSocialConnections() {
                List<UserSocialConnectionDtoOut> connections = new ArrayList<>();
                UserSocialConnectionDtoOut connection = new UserSocialConnectionDtoOut();
                connections.add(connection);

                dto.setSocialConnections(connections);
                assertThat(dto.getSocialConnections()).hasSize(1);
            }

            @Test
            @DisplayName("should get and set premium from child class")
            void shouldGetAndSetPremiumFromChildClass() {
                dto.setPremium(true);
                assertThat(dto.getPremium()).isTrue();
            }
        }

        @Nested
        @DisplayName("Inherited Field Tests")
        class InheritedFieldTests {

            @Test
            @DisplayName("should inherit id from parent")
            void shouldInheritIdFromParent() {
                dto.setId(789L);
                assertThat(dto.getId()).isEqualTo(789L);
            }

            @Test
            @DisplayName("should inherit name from parent")
            void shouldInheritNameFromParent() {
                dto.setName("Inherited Name");
                assertThat(dto.getName()).isEqualTo("Inherited Name");
            }

            @Test
            @DisplayName("should inherit profilePicture from parent")
            void shouldInheritProfilePictureFromParent() {
                dto.setProfilePicture("https://example.com/inherited.jpg");
                assertThat(dto.getProfilePicture()).isEqualTo("https://example.com/inherited.jpg");
            }

            @Test
            @DisplayName("should inherit accountStatus from parent")
            void shouldInheritAccountStatusFromParent() {
                AccountStatusDtoOut status = AccountStatusDtoOut.builder()
                        .value("ACTIVE")
                        .build();
                dto.setAccountStatus(status);
                assertThat(dto.getAccountStatus().getValue()).isEqualTo("ACTIVE");
            }
        }
    }

    // ==================== UserDtoOut Tests ====================

    @Nested
    @DisplayName("UserDtoOut Tests")
    class UserDtoOutTests {

        private UserDtoOut dto;

        @BeforeEach
        void setUp() {
            dto = new UserDtoOut();
        }

        @Nested
        @DisplayName("Getter and Setter Tests")
        class GetterSetterTests {

            @Test
            @DisplayName("should get and set id")
            void shouldGetAndSetId() {
                dto.setId(100L);
                assertThat(dto.getId()).isEqualTo(100L);
            }

            @Test
            @DisplayName("should get and set firebaseUserId")
            void shouldGetAndSetFirebaseUserId() {
                dto.setFirebaseUserId("firebase-uid-123");
                assertThat(dto.getFirebaseUserId()).isEqualTo("firebase-uid-123");
            }

            @Test
            @DisplayName("should get and set userType")
            void shouldGetAndSetUserType() {
                UserTypeDtoOut userType = UserTypeDtoOut.builder()
                        .value("COMPANY")
                        .build();
                dto.setUserType(userType);
                assertThat(dto.getUserType().getValue()).isEqualTo("COMPANY");
            }

            @Test
            @DisplayName("should get and set email")
            void shouldGetAndSetEmail() {
                dto.setEmail("user@example.com");
                assertThat(dto.getEmail()).isEqualTo("user@example.com");
            }

            @Test
            @DisplayName("should get and set firstName")
            void shouldGetAndSetFirstName() {
                dto.setFirstName("Jane");
                assertThat(dto.getFirstName()).isEqualTo("Jane");
            }

            @Test
            @DisplayName("should get and set lastName")
            void shouldGetAndSetLastName() {
                dto.setLastName("Smith");
                assertThat(dto.getLastName()).isEqualTo("Smith");
            }

            @Test
            @DisplayName("should get and set name")
            void shouldGetAndSetName() {
                dto.setName("Jane Smith");
                assertThat(dto.getName()).isEqualTo("Jane Smith");
            }

            @Test
            @DisplayName("should get and set profilePicture")
            void shouldGetAndSetProfilePicture() {
                dto.setProfilePicture("https://example.com/photo.jpg");
                assertThat(dto.getProfilePicture()).isEqualTo("https://example.com/photo.jpg");
            }

            @Test
            @DisplayName("should get and set addresses")
            void shouldGetAndSetAddresses() {
                List<AddressNoUserDtoOut> addresses = new ArrayList<>();
                dto.setAddresses(addresses);
                assertThat(dto.getAddresses()).isEmpty();
            }

            @Test
            @DisplayName("should get and set phoneNumber")
            void shouldGetAndSetPhoneNumber() {
                dto.setPhoneNumber("+1234567890");
                assertThat(dto.getPhoneNumber()).isEqualTo("+1234567890");
            }

            @Test
            @DisplayName("should get and set noteFromAdmin")
            void shouldGetAndSetNoteFromAdmin() {
                dto.setNoteFromAdmin("Admin note");
                assertThat(dto.getNoteFromAdmin()).isEqualTo("Admin note");
            }

            @Test
            @DisplayName("should get and set accountStatus")
            void shouldGetAndSetAccountStatus() {
                AccountStatusDtoOut status = AccountStatusDtoOut.builder()
                        .value("ACTIVE")
                        .build();
                dto.setAccountStatus(status);
                assertThat(dto.getAccountStatus().getValue()).isEqualTo("ACTIVE");
            }

            @Test
            @DisplayName("should get and set createdTime")
            void shouldGetAndSetCreatedTime() {
                LocalDateTime now = LocalDateTime.now();
                dto.setCreatedTime(now);
                assertThat(dto.getCreatedTime()).isEqualTo(now);
            }

            @Test
            @DisplayName("should get and set lastUpdateTime")
            void shouldGetAndSetLastUpdateTime() {
                LocalDateTime now = LocalDateTime.now();
                dto.setLastUpdateTime(now);
                assertThat(dto.getLastUpdateTime()).isEqualTo(now);
            }

            @Test
            @DisplayName("should get and set updater")
            void shouldGetAndSetUpdater() {
                dto.setUpdater("admin123");
                assertThat(dto.getUpdater()).isEqualTo("admin123");
            }

            @Test
            @DisplayName("should get and set socialConnections")
            void shouldGetAndSetSocialConnections() {
                List<UserSocialConnectionDtoOut> connections = new ArrayList<>();
                dto.setSocialConnections(connections);
                assertThat(dto.getSocialConnections()).isEmpty();
            }

            @Test
            @DisplayName("should get and set companyDescription")
            void shouldGetAndSetCompanyDescription() {
                dto.setCompanyDescription("A company");
                assertThat(dto.getCompanyDescription()).isEqualTo("A company");
            }

            @Test
            @DisplayName("should get and set nip")
            void shouldGetAndSetNip() {
                dto.setNip("1234567890");
                assertThat(dto.getNip()).isEqualTo("1234567890");
            }

            @Test
            @DisplayName("should get and set premium")
            void shouldGetAndSetPremium() {
                dto.setPremium(true);
                assertThat(dto.getPremium()).isTrue();
            }

            @Test
            @DisplayName("should get and set profileComplete")
            void shouldGetAndSetProfileComplete() {
                dto.setProfileComplete(true);
                assertThat(dto.getProfileComplete()).isTrue();
            }

            @Test
            @DisplayName("should get and set profileMissingFields")
            void shouldGetAndSetProfileMissingFields() {
                List<String> missingFields = List.of("email", "phoneNumber");
                dto.setProfileMissingFields(missingFields);
                assertThat(dto.getProfileMissingFields()).containsExactly("email", "phoneNumber");
            }
        }

        @Test
        @DisplayName("should have null values by default")
        void shouldHaveNullValuesByDefault() {
            UserDtoOut newDto = new UserDtoOut();

            assertThat(newDto.getId()).isNull();
            assertThat(newDto.getFirebaseUserId()).isNull();
            assertThat(newDto.getUserType()).isNull();
            assertThat(newDto.getEmail()).isNull();
            assertThat(newDto.getFirstName()).isNull();
            assertThat(newDto.getLastName()).isNull();
            assertThat(newDto.getName()).isNull();
            assertThat(newDto.getProfilePicture()).isNull();
            assertThat(newDto.getAddresses()).isNull();
            assertThat(newDto.getPhoneNumber()).isNull();
            assertThat(newDto.getNoteFromAdmin()).isNull();
            assertThat(newDto.getAccountStatus()).isNull();
            assertThat(newDto.getCreatedTime()).isNull();
            assertThat(newDto.getLastUpdateTime()).isNull();
            assertThat(newDto.getUpdater()).isNull();
            assertThat(newDto.getSocialConnections()).isNull();
            assertThat(newDto.getCompanyDescription()).isNull();
            assertThat(newDto.getNip()).isNull();
            assertThat(newDto.getPremium()).isNull();
            assertThat(newDto.getProfileComplete()).isNull();
            assertThat(newDto.getProfileMissingFields()).isNull();
        }
    }

    // ==================== DeletionBlocker Tests ====================

    @Nested
    @DisplayName("DeletionBlocker Tests")
    class DeletionBlockerTests {

        @Nested
        @DisplayName("Builder Tests")
        class BuilderTests {

            @Test
            @DisplayName("should build DeletionBlocker with all fields")
            void shouldBuildDeletionBlockerWithAllFields() {
                List<Long> entityIds = List.of(1L, 2L, 3L);

                DeletionBlocker blocker = DeletionBlocker.builder()
                        .category(DeletionBlockerCategory.ACTIVE_OPPORTUNITIES)
                        .reason("Active opportunities exist")
                        .description("User has 3 active opportunities")
                        .count(3)
                        .entityIds(entityIds)
                        .entityType("Opportunity")
                        .entityDescription("Marketing campaign opportunities")
                        .build();

                assertThat(blocker.getCategory()).isEqualTo(DeletionBlockerCategory.ACTIVE_OPPORTUNITIES);
                assertThat(blocker.getReason()).isEqualTo("Active opportunities exist");
                assertThat(blocker.getDescription()).isEqualTo("User has 3 active opportunities");
                assertThat(blocker.getCount()).isEqualTo(3);
                assertThat(blocker.getEntityIds()).containsExactly(1L, 2L, 3L);
                assertThat(blocker.getEntityType()).isEqualTo("Opportunity");
                assertThat(blocker.getEntityDescription()).isEqualTo("Marketing campaign opportunities");
            }

            @Test
            @DisplayName("should build DeletionBlocker with minimal fields")
            void shouldBuildDeletionBlockerWithMinimalFields() {
                DeletionBlocker blocker = DeletionBlocker.builder()
                        .category(DeletionBlockerCategory.LAST_ADMIN)
                        .build();

                assertThat(blocker.getCategory()).isEqualTo(DeletionBlockerCategory.LAST_ADMIN);
                assertThat(blocker.getReason()).isNull();
                assertThat(blocker.getCount()).isNull();
            }
        }

        @Nested
        @DisplayName("Getter and Setter Tests")
        class GetterSetterTests {

            private DeletionBlocker blocker;

            @BeforeEach
            void setUp() {
                blocker = new DeletionBlocker();
            }

            @Test
            @DisplayName("should get and set category")
            void shouldGetAndSetCategory() {
                blocker.setCategory(DeletionBlockerCategory.OPEN_SUPPORT_TICKETS);
                assertThat(blocker.getCategory()).isEqualTo(DeletionBlockerCategory.OPEN_SUPPORT_TICKETS);
            }

            @Test
            @DisplayName("should get and set reason")
            void shouldGetAndSetReason() {
                blocker.setReason("Tickets are open");
                assertThat(blocker.getReason()).isEqualTo("Tickets are open");
            }

            @Test
            @DisplayName("should get and set description")
            void shouldGetAndSetDescription() {
                blocker.setDescription("User has open support tickets");
                assertThat(blocker.getDescription()).isEqualTo("User has open support tickets");
            }

            @Test
            @DisplayName("should get and set count")
            void shouldGetAndSetCount() {
                blocker.setCount(5);
                assertThat(blocker.getCount()).isEqualTo(5);
            }

            @Test
            @DisplayName("should get and set entityIds")
            void shouldGetAndSetEntityIds() {
                blocker.setEntityIds(List.of(10L, 20L));
                assertThat(blocker.getEntityIds()).containsExactly(10L, 20L);
            }

            @Test
            @DisplayName("should get and set entityType")
            void shouldGetAndSetEntityType() {
                blocker.setEntityType("SupportTicket");
                assertThat(blocker.getEntityType()).isEqualTo("SupportTicket");
            }

            @Test
            @DisplayName("should get and set entityDescription")
            void shouldGetAndSetEntityDescription() {
                blocker.setEntityDescription("Support tickets requiring attention");
                assertThat(blocker.getEntityDescription()).isEqualTo("Support tickets requiring attention");
            }
        }

        @Nested
        @DisplayName("Equals and HashCode Tests")
        class EqualsHashCodeTests {

            @Test
            @DisplayName("should be equal when all fields match")
            void shouldBeEqualWhenAllFieldsMatch() {
                DeletionBlocker blocker1 = DeletionBlocker.builder()
                        .category(DeletionBlockerCategory.ACTIVE_OPPORTUNITIES)
                        .reason("Reason")
                        .count(1)
                        .build();

                DeletionBlocker blocker2 = DeletionBlocker.builder()
                        .category(DeletionBlockerCategory.ACTIVE_OPPORTUNITIES)
                        .reason("Reason")
                        .count(1)
                        .build();

                assertThat(blocker1).isEqualTo(blocker2);
                assertThat(blocker1.hashCode()).isEqualTo(blocker2.hashCode());
            }
        }

        @Nested
        @DisplayName("AllArgsConstructor Tests")
        class AllArgsConstructorTests {

            @Test
            @DisplayName("should create with all args constructor")
            void shouldCreateWithAllArgsConstructor() {
                List<Long> ids = List.of(1L);
                DeletionBlocker blocker = new DeletionBlocker(
                        DeletionBlockerCategory.RECENT_ACTIVITY,
                        "Recent activity",
                        "User had recent activity",
                        1,
                        ids,
                        "Activity",
                        "Recent user activity"
                );

                assertThat(blocker.getCategory()).isEqualTo(DeletionBlockerCategory.RECENT_ACTIVITY);
                assertThat(blocker.getReason()).isEqualTo("Recent activity");
                assertThat(blocker.getCount()).isEqualTo(1);
            }
        }
    }

    // ==================== DeletionBlockerCategory Tests ====================

    @Nested
    @DisplayName("DeletionBlockerCategory Enum Tests")
    class DeletionBlockerCategoryTests {

        @Test
        @DisplayName("should have 9 blocker categories")
        void shouldHave9BlockerCategories() {
            assertThat(DeletionBlockerCategory.values()).hasSize(9);
        }

        @Test
        @DisplayName("should contain all expected categories")
        void shouldContainAllExpectedCategories() {
            assertThat(DeletionBlockerCategory.values()).containsExactlyInAnyOrder(
                    DeletionBlockerCategory.ACTIVE_OPPORTUNITIES,
                    DeletionBlockerCategory.PENDING_OPPORTUNITIES,
                    DeletionBlockerCategory.ACTIVE_PARTNERSHIP_OPPORTUNITIES,
                    DeletionBlockerCategory.OPPORTUNITIES_WITH_APPLICATIONS,
                    DeletionBlockerCategory.LAST_ADMIN,
                    DeletionBlockerCategory.OPEN_SUPPORT_TICKETS,
                    DeletionBlockerCategory.SUPPORT_TICKETS_HISTORY,
                    DeletionBlockerCategory.RECENT_ACTIVITY,
                    DeletionBlockerCategory.DATA_RETENTION_REQUIRED
            );
        }

        @ParameterizedTest
        @EnumSource(DeletionBlockerCategory.class)
        @DisplayName("all categories should have non-null colorTheme")
        void allCategoriesShouldHaveNonNullColorTheme(DeletionBlockerCategory category) {
            assertThat(category.getColorTheme()).isNotBlank();
        }

        @ParameterizedTest
        @EnumSource(DeletionBlockerCategory.class)
        @DisplayName("all categories should have non-null icon")
        void allCategoriesShouldHaveNonNullIcon(DeletionBlockerCategory category) {
            assertThat(category.getIcon()).isNotBlank();
        }

        @Test
        @DisplayName("ACTIVE_OPPORTUNITIES should have warning colorTheme")
        void activeOpportunitiesShouldHaveWarningColorTheme() {
            assertThat(DeletionBlockerCategory.ACTIVE_OPPORTUNITIES.getColorTheme()).isEqualTo("warning");
        }

        @Test
        @DisplayName("LAST_ADMIN should have danger colorTheme")
        void lastAdminShouldHaveDangerColorTheme() {
            assertThat(DeletionBlockerCategory.LAST_ADMIN.getColorTheme()).isEqualTo("danger");
        }

        @Test
        @DisplayName("PENDING_OPPORTUNITIES should have info colorTheme")
        void pendingOpportunitiesShouldHaveInfoColorTheme() {
            assertThat(DeletionBlockerCategory.PENDING_OPPORTUNITIES.getColorTheme()).isEqualTo("info");
        }

        @Test
        @DisplayName("ACTIVE_OPPORTUNITIES should have briefcase icon")
        void activeOpportunitiesShouldHaveBriefcaseIcon() {
            assertThat(DeletionBlockerCategory.ACTIVE_OPPORTUNITIES.getIcon()).isEqualTo("briefcase");
        }

        @Test
        @DisplayName("LAST_ADMIN should have shield-alert icon")
        void lastAdminShouldHaveShieldAlertIcon() {
            assertThat(DeletionBlockerCategory.LAST_ADMIN.getIcon()).isEqualTo("shield-alert");
        }
    }

    // ==================== DeletionEligibilityDto Tests ====================

    @Nested
    @DisplayName("DeletionEligibilityDto Tests")
    class DeletionEligibilityDtoTests {

        @Nested
        @DisplayName("Builder Tests")
        class BuilderTests {

            @Test
            @DisplayName("should build DeletionEligibilityDto with all fields")
            void shouldBuildDeletionEligibilityDtoWithAllFields() {
                List<DeletionBlocker> softBlockers = new ArrayList<>();
                List<DeletionBlocker> permanentBlockers = new ArrayList<>();

                DeletionEligibilityDto dto = DeletionEligibilityDto.builder()
                        .userId(1L)
                        .firebaseUserId("firebase-123")
                        .userEmail("user@example.com")
                        .userType("INFLUENCER")
                        .canSoftDelete(true)
                        .canPermanentDelete(false)
                        .softDeleteBlockers(softBlockers)
                        .permanentDeleteBlockers(permanentBlockers)
                        .summary("User can be soft deleted but not permanently deleted")
                        .build();

                assertThat(dto.getUserId()).isEqualTo(1L);
                assertThat(dto.getFirebaseUserId()).isEqualTo("firebase-123");
                assertThat(dto.getUserEmail()).isEqualTo("user@example.com");
                assertThat(dto.getUserType()).isEqualTo("INFLUENCER");
                assertThat(dto.isCanSoftDelete()).isTrue();
                assertThat(dto.isCanPermanentDelete()).isFalse();
                assertThat(dto.getSoftDeleteBlockers()).isEmpty();
                assertThat(dto.getPermanentDeleteBlockers()).isEmpty();
                assertThat(dto.getSummary()).isEqualTo("User can be soft deleted but not permanently deleted");
            }

            @Test
            @DisplayName("should build with default boolean values as false")
            void shouldBuildWithDefaultBooleanValuesAsFalse() {
                DeletionEligibilityDto dto = DeletionEligibilityDto.builder().build();

                assertThat(dto.isCanSoftDelete()).isFalse();
                assertThat(dto.isCanPermanentDelete()).isFalse();
            }
        }

        @Nested
        @DisplayName("Getter and Setter Tests")
        class GetterSetterTests {

            private DeletionEligibilityDto dto;

            @BeforeEach
            void setUp() {
                dto = new DeletionEligibilityDto();
            }

            @Test
            @DisplayName("should get and set userId")
            void shouldGetAndSetUserId() {
                dto.setUserId(123L);
                assertThat(dto.getUserId()).isEqualTo(123L);
            }

            @Test
            @DisplayName("should get and set firebaseUserId")
            void shouldGetAndSetFirebaseUserId() {
                dto.setFirebaseUserId("firebase-456");
                assertThat(dto.getFirebaseUserId()).isEqualTo("firebase-456");
            }

            @Test
            @DisplayName("should get and set userEmail")
            void shouldGetAndSetUserEmail() {
                dto.setUserEmail("test@test.com");
                assertThat(dto.getUserEmail()).isEqualTo("test@test.com");
            }

            @Test
            @DisplayName("should get and set userType")
            void shouldGetAndSetUserType() {
                dto.setUserType("COMPANY");
                assertThat(dto.getUserType()).isEqualTo("COMPANY");
            }

            @Test
            @DisplayName("should get and set canSoftDelete")
            void shouldGetAndSetCanSoftDelete() {
                dto.setCanSoftDelete(true);
                assertThat(dto.isCanSoftDelete()).isTrue();
            }

            @Test
            @DisplayName("should get and set canPermanentDelete")
            void shouldGetAndSetCanPermanentDelete() {
                dto.setCanPermanentDelete(true);
                assertThat(dto.isCanPermanentDelete()).isTrue();
            }

            @Test
            @DisplayName("should get and set softDeleteBlockers")
            void shouldGetAndSetSoftDeleteBlockers() {
                List<DeletionBlocker> blockers = new ArrayList<>();
                blockers.add(DeletionBlocker.builder()
                        .category(DeletionBlockerCategory.ACTIVE_OPPORTUNITIES)
                        .build());
                dto.setSoftDeleteBlockers(blockers);
                assertThat(dto.getSoftDeleteBlockers()).hasSize(1);
            }

            @Test
            @DisplayName("should get and set permanentDeleteBlockers")
            void shouldGetAndSetPermanentDeleteBlockers() {
                List<DeletionBlocker> blockers = new ArrayList<>();
                blockers.add(DeletionBlocker.builder()
                        .category(DeletionBlockerCategory.DATA_RETENTION_REQUIRED)
                        .build());
                dto.setPermanentDeleteBlockers(blockers);
                assertThat(dto.getPermanentDeleteBlockers()).hasSize(1);
            }

            @Test
            @DisplayName("should get and set summary")
            void shouldGetAndSetSummary() {
                dto.setSummary("Summary text");
                assertThat(dto.getSummary()).isEqualTo("Summary text");
            }
        }

        @Nested
        @DisplayName("AllArgsConstructor Tests")
        class AllArgsConstructorTests {

            @Test
            @DisplayName("should create with all args constructor")
            void shouldCreateWithAllArgsConstructor() {
                List<DeletionBlocker> softBlockers = new ArrayList<>();
                List<DeletionBlocker> permanentBlockers = new ArrayList<>();

                DeletionEligibilityDto dto = new DeletionEligibilityDto(
                        1L, "firebase-1", "user@test.com", "ADMIN",
                        true, true, softBlockers, permanentBlockers, "Summary"
                );

                assertThat(dto.getUserId()).isEqualTo(1L);
                assertThat(dto.getFirebaseUserId()).isEqualTo("firebase-1");
                assertThat(dto.getUserEmail()).isEqualTo("user@test.com");
                assertThat(dto.getUserType()).isEqualTo("ADMIN");
                assertThat(dto.isCanSoftDelete()).isTrue();
                assertThat(dto.isCanPermanentDelete()).isTrue();
                assertThat(dto.getSummary()).isEqualTo("Summary");
            }
        }
    }

    // ==================== DefaultNoteService Tests ====================

    @Nested
    @DisplayName("DefaultNoteService Tests")
    class DefaultNoteServiceTests {

        @Mock
        private MessageSource messageSource;

        private DefaultNoteService defaultNoteService;

        @BeforeEach
        void setUp() {
            defaultNoteService = new DefaultNoteService(messageSource);
        }

        @Nested
        @DisplayName("GetDefaultNote for INFLUENCER Tests")
        class GetDefaultNoteForInfluencerTests {

            @Test
            @DisplayName("should return localized message for INFLUENCER")
            void shouldReturnLocalizedMessageForInfluencer() {
                LocaleContextHolder.setLocale(Locale.ENGLISH);
                when(messageSource.getMessage(
                        eq("user.default.note.influencer"),
                        isNull(),
                        anyString(),
                        eq(Locale.ENGLISH)))
                        .thenReturn("Complete your data to activate account.");

                String note = defaultNoteService.getDefaultNote(UserType.INFLUENCER);

                assertThat(note).isEqualTo("Complete your data to activate account.");
            }

            @Test
            @DisplayName("should return Polish message for INFLUENCER when locale is Polish")
            void shouldReturnPolishMessageForInfluencer() {
                Locale polish = Locale.forLanguageTag("pl");
                LocaleContextHolder.setLocale(polish);
                when(messageSource.getMessage(
                        eq("user.default.note.influencer"),
                        isNull(),
                        anyString(),
                        eq(polish)))
                        .thenReturn("Uzupelnij swoje dane, zeby aktywowac konto.");

                String note = defaultNoteService.getDefaultNote(UserType.INFLUENCER);

                assertThat(note).contains("Uzupelnij");
            }

            @Test
            @DisplayName("should return fallback message when MessageSource fails for INFLUENCER")
            void shouldReturnFallbackMessageWhenMessageSourceFailsForInfluencer() {
                LocaleContextHolder.setLocale(Locale.ENGLISH);
                when(messageSource.getMessage(
                        eq("user.default.note.influencer"),
                        isNull(),
                        anyString(),
                        any(Locale.class)))
                        .thenThrow(new RuntimeException("MessageSource failed"));

                String note = defaultNoteService.getDefaultNote(UserType.INFLUENCER);

                // Should return Polish fallback - "dane" is ASCII part of the message
                assertThat(note).containsIgnoringCase("dane");
            }
        }

        @Nested
        @DisplayName("GetDefaultNote for COMPANY Tests")
        class GetDefaultNoteForCompanyTests {

            @Test
            @DisplayName("should return localized message for COMPANY")
            void shouldReturnLocalizedMessageForCompany() {
                LocaleContextHolder.setLocale(Locale.ENGLISH);
                when(messageSource.getMessage(
                        eq("user.default.note.company"),
                        isNull(),
                        anyString(),
                        eq(Locale.ENGLISH)))
                        .thenReturn("Complete your company data to activate account.");

                String note = defaultNoteService.getDefaultNote(UserType.COMPANY);

                assertThat(note).isEqualTo("Complete your company data to activate account.");
            }

            @Test
            @DisplayName("should return fallback message when MessageSource fails for COMPANY")
            void shouldReturnFallbackMessageWhenMessageSourceFailsForCompany() {
                LocaleContextHolder.setLocale(Locale.ENGLISH);
                when(messageSource.getMessage(
                        eq("user.default.note.company"),
                        isNull(),
                        anyString(),
                        any(Locale.class)))
                        .thenThrow(new RuntimeException("MessageSource failed"));

                String note = defaultNoteService.getDefaultNote(UserType.COMPANY);

                // Should return Polish fallback
                assertThat(note).contains("firmy");
            }
        }

        @Nested
        @DisplayName("GetDefaultNote for ADMIN Types Tests")
        class GetDefaultNoteForAdminTypesTests {

            @Test
            @DisplayName("should use INFLUENCER key for ADMIN")
            void shouldUseInfluencerKeyForAdmin() {
                LocaleContextHolder.setLocale(Locale.ENGLISH);
                when(messageSource.getMessage(
                        eq("user.default.note.influencer"),
                        isNull(),
                        anyString(),
                        eq(Locale.ENGLISH)))
                        .thenReturn("Admin note using influencer key");

                String note = defaultNoteService.getDefaultNote(UserType.ADMIN);

                assertThat(note).isEqualTo("Admin note using influencer key");
            }

            @Test
            @DisplayName("should use INFLUENCER key for PENDING_ADMIN")
            void shouldUseInfluencerKeyForPendingAdmin() {
                LocaleContextHolder.setLocale(Locale.ENGLISH);
                when(messageSource.getMessage(
                        eq("user.default.note.influencer"),
                        isNull(),
                        anyString(),
                        eq(Locale.ENGLISH)))
                        .thenReturn("Pending admin note using influencer key");

                String note = defaultNoteService.getDefaultNote(UserType.PENDING_ADMIN);

                assertThat(note).isEqualTo("Pending admin note using influencer key");
            }
        }

        @Nested
        @DisplayName("Locale Handling Tests")
        class LocaleHandlingTests {

            @Test
            @DisplayName("should use LocaleContextHolder locale")
            void shouldUseLocaleContextHolderLocale() {
                Locale german = Locale.GERMAN;
                LocaleContextHolder.setLocale(german);
                when(messageSource.getMessage(
                        anyString(),
                        isNull(),
                        anyString(),
                        eq(german)))
                        .thenReturn("German translation");

                String note = defaultNoteService.getDefaultNote(UserType.INFLUENCER);

                assertThat(note).isEqualTo("German translation");
            }

            @Test
            @DisplayName("should handle null message gracefully")
            void shouldHandleNullMessageGracefully() {
                LocaleContextHolder.setLocale(Locale.ENGLISH);
                when(messageSource.getMessage(
                        eq("user.default.note.influencer"),
                        isNull(),
                        anyString(),
                        eq(Locale.ENGLISH)))
                        .thenReturn(null);

                String note = defaultNoteService.getDefaultNote(UserType.INFLUENCER);

                // Returns null from getMessage (which uses fallback)
                assertThat(note).isNull();
            }
        }
    }

    // ==================== ProfileFieldCriticality Additional Tests ====================

    @Nested
    @DisplayName("ProfileFieldCriticality Additional Tests")
    class ProfileFieldCriticalityAdditionalTests {

        @Test
        @DisplayName("should return CRITICAL for firstName")
        void shouldReturnCriticalForFirstName() {
            assertThat(ProfileFieldCriticality.forField("firstName"))
                    .isEqualTo(ProfileFieldCriticality.CRITICAL);
        }

        @Test
        @DisplayName("should return CRITICAL for lastName")
        void shouldReturnCriticalForLastName() {
            assertThat(ProfileFieldCriticality.forField("lastName"))
                    .isEqualTo(ProfileFieldCriticality.CRITICAL);
        }

        @Test
        @DisplayName("should return CRITICAL for email")
        void shouldReturnCriticalForEmail() {
            assertThat(ProfileFieldCriticality.forField("email"))
                    .isEqualTo(ProfileFieldCriticality.CRITICAL);
        }

        @Test
        @DisplayName("should return CRITICAL for phoneNumber")
        void shouldReturnCriticalForPhoneNumber() {
            assertThat(ProfileFieldCriticality.forField("phoneNumber"))
                    .isEqualTo(ProfileFieldCriticality.CRITICAL);
        }

        @Test
        @DisplayName("should return CRITICAL for name")
        void shouldReturnCriticalForName() {
            assertThat(ProfileFieldCriticality.forField("name"))
                    .isEqualTo(ProfileFieldCriticality.CRITICAL);
        }

        @Test
        @DisplayName("should return CRITICAL for nip")
        void shouldReturnCriticalForNip() {
            assertThat(ProfileFieldCriticality.forField("nip"))
                    .isEqualTo(ProfileFieldCriticality.CRITICAL);
        }

        @Test
        @DisplayName("should return NON_CRITICAL for profilePicture")
        void shouldReturnNonCriticalForProfilePicture() {
            assertThat(ProfileFieldCriticality.forField("profilePicture"))
                    .isEqualTo(ProfileFieldCriticality.NON_CRITICAL);
        }

        @Test
        @DisplayName("should return NON_CRITICAL for companyDescription")
        void shouldReturnNonCriticalForCompanyDescription() {
            assertThat(ProfileFieldCriticality.forField("companyDescription"))
                    .isEqualTo(ProfileFieldCriticality.NON_CRITICAL);
        }

        @Test
        @DisplayName("should return NON_CRITICAL for addresses")
        void shouldReturnNonCriticalForAddresses() {
            assertThat(ProfileFieldCriticality.forField("addresses"))
                    .isEqualTo(ProfileFieldCriticality.NON_CRITICAL);
        }

        @Test
        @DisplayName("isCriticalField should return true for critical fields")
        void isCriticalFieldShouldReturnTrueForCriticalFields() {
            assertThat(ProfileFieldCriticality.isCriticalField("email")).isTrue();
            assertThat(ProfileFieldCriticality.isCriticalField("firstName")).isTrue();
            assertThat(ProfileFieldCriticality.isCriticalField("nip")).isTrue();
        }

        @Test
        @DisplayName("isCriticalField should return false for non-critical fields")
        void isCriticalFieldShouldReturnFalseForNonCriticalFields() {
            assertThat(ProfileFieldCriticality.isCriticalField("profilePicture")).isFalse();
            assertThat(ProfileFieldCriticality.isCriticalField("companyDescription")).isFalse();
        }

        @Test
        @DisplayName("getCriticalFields should return 6 critical fields")
        void getCriticalFieldsShouldReturn6CriticalFields() {
            assertThat(ProfileFieldCriticality.getCriticalFields()).hasSize(6);
        }

        @Test
        @DisplayName("getNonCriticalFields should return 5 non-critical fields")
        void getNonCriticalFieldsShouldReturn5NonCriticalFields() {
            assertThat(ProfileFieldCriticality.getNonCriticalFields()).hasSize(5);
        }
    }

    // ==================== AccountStatus Additional Tests ====================

    @Nested
    @DisplayName("AccountStatus Additional Tests")
    class AccountStatusAdditionalTests {

        @Test
        @DisplayName("IN_VALIDATION should have clock icon")
        void inValidationShouldHaveClockIcon() {
            assertThat(AccountStatus.IN_VALIDATION.getIcon()).isEqualTo("clock");
        }

        @Test
        @DisplayName("IN_VALIDATION should have warning colorTheme")
        void inValidationShouldHaveWarningColorTheme() {
            assertThat(AccountStatus.IN_VALIDATION.getColorTheme()).isEqualTo("warning");
        }

        @Test
        @DisplayName("TO_BE_DELETED should have user-x icon")
        void toBeDeletedShouldHaveUserXIcon() {
            assertThat(AccountStatus.TO_BE_DELETED.getIcon()).isEqualTo("user-x");
        }

        @Test
        @DisplayName("TO_BE_DELETED should have danger colorTheme")
        void toBeDeletedShouldHaveDangerColorTheme() {
            assertThat(AccountStatus.TO_BE_DELETED.getColorTheme()).isEqualTo("danger");
        }

        @ParameterizedTest
        @EnumSource(AccountStatus.class)
        @DisplayName("all statuses should have description containing text")
        void allStatusesShouldHaveDescriptionContainingText(AccountStatus status) {
            assertThat(status.getDescription()).isNotBlank();
            assertThat(status.getDescription().length()).isGreaterThan(10);
        }

        @Test
        @DisplayName("INACTIVE aliases should include disabled")
        void inactiveAliasesShouldIncludeDisabled() {
            assertThat(AccountStatus.INACTIVE.getAliases()).contains("disabled");
        }

        @Test
        @DisplayName("IN_VALIDATION aliases should include pending and under-review")
        void inValidationAliasesShouldIncludePendingAndUnderReview() {
            assertThat(AccountStatus.IN_VALIDATION.getAliases()).contains("pending", "under-review");
        }

        @Test
        @DisplayName("BANNED aliases should include suspended, blocked, prohibited")
        void bannedAliasesShouldIncludeSuspendedBlockedProhibited() {
            assertThat(AccountStatus.BANNED.getAliases()).contains("suspended", "blocked", "prohibited");
        }
    }

    // ==================== UserType Additional Tests ====================

    @Nested
    @DisplayName("UserType Additional Tests")
    class UserTypeAdditionalTests {

        @Test
        @DisplayName("ADMIN should be first enum value")
        void adminShouldBeFirstEnumValue() {
            assertThat(UserType.values()[0]).isEqualTo(UserType.ADMIN);
        }

        @Test
        @DisplayName("ordinal values should be sequential")
        void ordinalValuesShouldBeSequential() {
            assertThat(UserType.ADMIN.ordinal()).isEqualTo(0);
            assertThat(UserType.PENDING_ADMIN.ordinal()).isEqualTo(1);
            assertThat(UserType.INFLUENCER.ordinal()).isEqualTo(2);
            assertThat(UserType.COMPANY.ordinal()).isEqualTo(3);
        }

        @ParameterizedTest
        @EnumSource(UserType.class)
        @DisplayName("valueOf should work for all types")
        void valueOfShouldWorkForAllTypes(UserType type) {
            assertThat(UserType.valueOf(type.name())).isEqualTo(type);
        }
    }

    // ==================== Edge Case Tests ====================

    @Nested
    @DisplayName("Edge Case Tests")
    class EdgeCaseTests {

        @Test
        @DisplayName("DeletionBlocker with empty entityIds list")
        void deletionBlockerWithEmptyEntityIdsList() {
            DeletionBlocker blocker = DeletionBlocker.builder()
                    .category(DeletionBlockerCategory.ACTIVE_OPPORTUNITIES)
                    .entityIds(new ArrayList<>())
                    .build();

            assertThat(blocker.getEntityIds()).isEmpty();
        }

        @Test
        @DisplayName("DeletionEligibilityDto with both delete flags true")
        void deletionEligibilityDtoWithBothDeleteFlagsTrue() {
            DeletionEligibilityDto dto = DeletionEligibilityDto.builder()
                    .canSoftDelete(true)
                    .canPermanentDelete(true)
                    .build();

            assertThat(dto.isCanSoftDelete()).isTrue();
            assertThat(dto.isCanPermanentDelete()).isTrue();
        }

        @Test
        @DisplayName("CompanyPublicProfileDto with empty addresses list")
        void companyPublicProfileDtoWithEmptyAddressesList() {
            CompanyPublicProfileDto dto = new CompanyPublicProfileDto();
            dto.setAddresses(new ArrayList<>());

            assertThat(dto.getAddresses()).isEmpty();
        }

        @Test
        @DisplayName("InfluencerForCompanyProfileDto with empty socialConnections list")
        void influencerForCompanyProfileDtoWithEmptySocialConnectionsList() {
            InfluencerForCompanyProfileDto dto = new InfluencerForCompanyProfileDto();
            dto.setSocialConnections(new ArrayList<>());

            assertThat(dto.getSocialConnections()).isEmpty();
        }

        @Test
        @DisplayName("UserDtoOut with empty profileMissingFields list")
        void userDtoOutWithEmptyProfileMissingFieldsList() {
            UserDtoOut dto = new UserDtoOut();
            dto.setProfileMissingFields(new ArrayList<>());

            assertThat(dto.getProfileMissingFields()).isEmpty();
        }

        @Test
        @DisplayName("AccountStatusDtoOut all boolean fields set to true")
        void accountStatusDtoOutAllBooleanFieldsSetToTrue() {
            AccountStatusDtoOut dto = AccountStatusDtoOut.builder()
                    .isActive(true)
                    .canLogin(true)
                    .isTerminal(true)
                    .build();

            assertThat(dto.isActive()).isTrue();
            assertThat(dto.isCanLogin()).isTrue();
            assertThat(dto.isTerminal()).isTrue();
        }
    }

    // ==================== Integration-Like Tests ====================

    @Nested
    @DisplayName("Integration-Like Tests")
    class IntegrationLikeTests {

        @Test
        @DisplayName("should create complete UserDtoOut with all nested DTOs")
        void shouldCreateCompleteUserDtoOutWithAllNestedDtos() {
            UserDtoOut dto = new UserDtoOut();
            dto.setId(1L);
            dto.setFirebaseUserId("firebase-complete-123");
            dto.setEmail("complete@example.com");
            dto.setFirstName("Complete");
            dto.setLastName("User");
            dto.setName("Complete User");

            UserTypeDtoOut userType = UserTypeDtoOut.builder()
                    .value("INFLUENCER")
                    .label("Influencer")
                    .originalLabel("INFLUENCER")
                    .build();
            dto.setUserType(userType);

            AccountStatusDtoOut status = AccountStatusDtoOut.builder()
                    .value("ACTIVE")
                    .label("Active")
                    .description("Account is active")
                    .originalLabel("ACTIVE")
                    .colorTheme("success")
                    .icon("user-check")
                    .isActive(true)
                    .canLogin(true)
                    .isTerminal(false)
                    .build();
            dto.setAccountStatus(status);

            dto.setProfileComplete(true);
            dto.setProfileMissingFields(new ArrayList<>());
            dto.setPremium(false);

            assertThat(dto.getId()).isEqualTo(1L);
            assertThat(dto.getUserType().getValue()).isEqualTo("INFLUENCER");
            assertThat(dto.getAccountStatus().isActive()).isTrue();
            assertThat(dto.getProfileComplete()).isTrue();
        }

        @Test
        @DisplayName("should create complete DeletionEligibilityDto with blockers")
        void shouldCreateCompleteDeletionEligibilityDtoWithBlockers() {
            DeletionBlocker softBlocker = DeletionBlocker.builder()
                    .category(DeletionBlockerCategory.ACTIVE_OPPORTUNITIES)
                    .reason("Has active opportunities")
                    .count(2)
                    .entityIds(List.of(1L, 2L))
                    .build();

            DeletionBlocker permanentBlocker = DeletionBlocker.builder()
                    .category(DeletionBlockerCategory.DATA_RETENTION_REQUIRED)
                    .reason("Data must be retained")
                    .count(0)
                    .build();

            DeletionEligibilityDto dto = DeletionEligibilityDto.builder()
                    .userId(100L)
                    .firebaseUserId("firebase-del-100")
                    .userEmail("delete@example.com")
                    .userType("COMPANY")
                    .canSoftDelete(false)
                    .canPermanentDelete(false)
                    .softDeleteBlockers(List.of(softBlocker))
                    .permanentDeleteBlockers(List.of(permanentBlocker))
                    .summary("User cannot be deleted due to active opportunities and data retention requirements")
                    .build();

            assertThat(dto.isCanSoftDelete()).isFalse();
            assertThat(dto.isCanPermanentDelete()).isFalse();
            assertThat(dto.getSoftDeleteBlockers()).hasSize(1);
            assertThat(dto.getPermanentDeleteBlockers()).hasSize(1);
            assertThat(dto.getSoftDeleteBlockers().get(0).getCategory())
                    .isEqualTo(DeletionBlockerCategory.ACTIVE_OPPORTUNITIES);
        }

        @Test
        @DisplayName("should create InfluencerForCompanyProfileDto with all fields")
        void shouldCreateInfluencerForCompanyProfileDtoWithAllFields() {
            InfluencerForCompanyProfileDto dto = new InfluencerForCompanyProfileDto();

            // Set parent fields
            dto.setId(500L);
            dto.setName("Full Influencer");
            dto.setProfilePicture("https://example.com/full.jpg");
            dto.setCreatedTime(LocalDateTime.now());
            dto.setPlatformName("Instagram");
            dto.setDisplayName("@fullinfluencer");
            dto.setProfileUrl("https://instagram.com/fullinfluencer");
            dto.setFollowersCount(50000);

            AccountStatusDtoOut status = AccountStatusDtoOut.builder()
                    .value("ACTIVE")
                    .isActive(true)
                    .build();
            dto.setAccountStatus(status);

            // Set child-specific fields
            UserTypeDtoOut userType = UserTypeDtoOut.builder()
                    .value("INFLUENCER")
                    .build();
            dto.setUserType(userType);
            dto.setEmail("full@influencer.com");
            dto.setFirstName("Full");
            dto.setLastName("Influencer");
            dto.setPhoneNumber("+48123456789");
            dto.setPremium(true);

            assertThat(dto.getId()).isEqualTo(500L);
            assertThat(dto.getName()).isEqualTo("Full Influencer");
            assertThat(dto.getUserType().getValue()).isEqualTo("INFLUENCER");
            assertThat(dto.getEmail()).isEqualTo("full@influencer.com");
            assertThat(dto.getFollowersCount()).isEqualTo(50000);
            assertThat(dto.getPremium()).isTrue();
        }
    }
}
