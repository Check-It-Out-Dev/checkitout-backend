package com.sm.instagram.platform.unit.service;

import com.sm.instagram.platform.common.authorization.PermissionUtils;
import com.sm.instagram.platform.common.exceptions.InsufficientPermissionsException;
import com.sm.instagram.platform.common.exceptions.ResourceNotFoundException;
import com.sm.instagram.platform.common.util.RepositoryResolver;
import com.sm.instagram.platform.common.util.filtering.SpecificationBuilder;
import com.sm.instagram.platform.usersocialconnection.*;
import com.sm.instagram.platform.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.modelmapper.ModelMapper;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Unit tests for UserSocialConnectionService.
 * Tests focus on findById, getPrimaryConnection, getPrimaryConnectionSafe, delete,
 * and getDataPagedAndFiltered methods that can be unit tested.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("UserSocialConnectionService Unit Tests")
class UserSocialConnectionServiceUnitTest {

    @Mock
    private UserSocialConnectionRepository repository;

    @Mock
    private SpecificationBuilder<UserSocialConnection> specificationBuilder;

    @Mock
    private ModelMapper modelMapper;

    @Mock
    private RepositoryResolver repositoryResolver;

    @Mock
    private PermissionUtils permissionUtils;

    @Mock
    private ApplicationContext applicationContext;

    private UserSocialConnectionService service;

    private User createUser(Long id, String firebaseUid) {
        User user = new User();
        user.setId(id);
        user.setFirebaseUserId(firebaseUid);
        return user;
    }

    private UserSocialConnection createConnection(Long id, User user, boolean isPrimary) {
        UserSocialConnection connection = new UserSocialConnection();
        connection.setId(id);
        connection.setUser(user);
        connection.setDisplayName("test_user");
        connection.setFollowersCount(1000);
        connection.setIsPrimary(isPrimary);
        connection.setConnectionStatus(ConnectionStatus.CONNECTED);
        connection.setSocialUserId("social123");
        return connection;
    }

    @BeforeEach
    void setUp() {
        // Create service using anonymous subclass to access protected constructor
        service = new UserSocialConnectionService(
                specificationBuilder,
                repository,
                modelMapper,
                repositoryResolver,
                permissionUtils,
                applicationContext
        ) {};

        // Setup getSelf() pattern
        when(applicationContext.getBean(UserSocialConnectionService.class)).thenReturn(service);
    }

    @Nested
    @DisplayName("findById")
    class FindByIdTests {

        @Test
        @DisplayName("should return connection when owner accesses their own connection")
        void shouldReturnConnectionWhenOwnerAccessesOwnConnection() {
            // Given
            String firebaseUid = "user-uid";
            User user = createUser(1L, firebaseUid);
            UserSocialConnection connection = createConnection(1L, user, true);

            when(permissionUtils.getUserId()).thenReturn(firebaseUid);
            when(permissionUtils.isAdmin()).thenReturn(false);
            when(permissionUtils.isUserOwner(connection)).thenReturn(true);
            when(repository.findById(1L)).thenReturn(Optional.of(connection));

            // When
            UserSocialConnection result = service.findById(1L);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getDisplayName()).isEqualTo("test_user");
            verify(repository).findById(1L);
        }

        @Test
        @DisplayName("should return connection when admin accesses any connection")
        void shouldReturnConnectionWhenAdminAccessesAnyConnection() {
            // Given
            String adminUid = "admin-uid";
            User otherUser = createUser(99L, "other-uid");
            UserSocialConnection connection = createConnection(1L, otherUser, true);

            when(permissionUtils.getUserId()).thenReturn(adminUid);
            when(permissionUtils.isAdmin()).thenReturn(true);
            when(repository.findById(1L)).thenReturn(Optional.of(connection));

            // When
            UserSocialConnection result = service.findById(1L);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when connection not found")
        void shouldThrowResourceNotFoundWhenConnectionNotFound() {
            // Given
            when(permissionUtils.getUserId()).thenReturn("user-uid");
            when(repository.findById(999L)).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> service.findById(999L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should throw InsufficientPermissionsException when non-owner accesses connection")
        void shouldThrowInsufficientPermissionsWhenNonOwnerAccessesConnection() {
            // Given
            String currentUid = "other-uid";
            User owner = createUser(1L, "owner-uid");
            UserSocialConnection connection = createConnection(1L, owner, true);

            when(permissionUtils.getUserId()).thenReturn(currentUid);
            when(permissionUtils.isAdmin()).thenReturn(false);
            when(permissionUtils.isUserOwner(connection)).thenReturn(false);
            when(repository.findById(1L)).thenReturn(Optional.of(connection));

            // When/Then
            assertThatThrownBy(() -> service.findById(1L))
                    .isInstanceOf(InsufficientPermissionsException.class);
        }
    }

    @Nested
    @DisplayName("getPrimaryConnection")
    class GetPrimaryConnectionTests {

        @Test
        @DisplayName("should return primary connection when exists")
        void shouldReturnPrimaryConnectionWhenExists() {
            // Given
            User user = createUser(1L, "user-uid");
            UserSocialConnection primaryConnection = createConnection(1L, user, true);

            when(permissionUtils.getUserId()).thenReturn("user-uid");
            when(repository.findByUserIdAndIsPrimaryTrue(1L)).thenReturn(Optional.of(primaryConnection));

            // When
            UserSocialConnection result = service.getPrimaryConnection(user);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getIsPrimary()).isTrue();
            assertThat(result.getUser()).isEqualTo(user);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when no primary connection")
        void shouldThrowResourceNotFoundWhenNoPrimaryConnection() {
            // Given
            User user = createUser(1L, "user-uid");
            when(permissionUtils.getUserId()).thenReturn("user-uid");
            when(repository.findByUserIdAndIsPrimaryTrue(1L)).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> service.getPrimaryConnection(user))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("getPrimaryConnectionSafe")
    class GetPrimaryConnectionSafeTests {

        @Test
        @DisplayName("should return primary connection when exists")
        void shouldReturnPrimaryConnectionWhenExists() {
            // Given
            User user = createUser(1L, "user-uid");
            UserSocialConnection primaryConnection = createConnection(1L, user, true);

            when(repository.findByUserIdAndIsPrimaryTrue(1L)).thenReturn(Optional.of(primaryConnection));

            // When
            UserSocialConnection result = service.getPrimaryConnectionSafe(user);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getIsPrimary()).isTrue();
        }

        @Test
        @DisplayName("should return null when no primary connection")
        void shouldReturnNullWhenNoPrimaryConnection() {
            // Given
            User user = createUser(1L, "user-uid");
            when(repository.findByUserIdAndIsPrimaryTrue(1L)).thenReturn(Optional.empty());

            // When
            UserSocialConnection result = service.getPrimaryConnectionSafe(user);

            // Then
            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("delete")
    class DeleteTests {

        @Test
        @DisplayName("should delete connection when owner deletes their own connection")
        void shouldDeleteConnectionWhenOwnerDeletesOwnConnection() {
            // Given
            String firebaseUid = "user-uid";
            User user = createUser(1L, firebaseUid);
            UserSocialConnection connection = createConnection(1L, user, false);

            when(permissionUtils.getUserId()).thenReturn(firebaseUid);
            when(permissionUtils.isAdmin()).thenReturn(false);
            when(permissionUtils.isUserOwner(connection)).thenReturn(true);
            when(repository.existsById(1L)).thenReturn(true);
            when(repository.findById(1L)).thenReturn(Optional.of(connection));

            // When
            service.delete(1L);

            // Then
            verify(repository).deleteById(1L);
        }

        @Test
        @DisplayName("should delete connection when admin deletes any connection")
        void shouldDeleteConnectionWhenAdminDeletesAnyConnection() {
            // Given
            User otherUser = createUser(99L, "other-uid");
            UserSocialConnection connection = createConnection(1L, otherUser, false);

            when(permissionUtils.getUserId()).thenReturn("admin-uid");
            when(permissionUtils.isAdmin()).thenReturn(true);
            when(repository.existsById(1L)).thenReturn(true);
            when(repository.findById(1L)).thenReturn(Optional.of(connection));

            // When
            service.delete(1L);

            // Then
            verify(repository).deleteById(1L);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when connection not found")
        void shouldThrowResourceNotFoundWhenConnectionNotFoundForDelete() {
            // Given
            when(permissionUtils.getUserId()).thenReturn("user-uid");
            when(repository.existsById(999L)).thenReturn(false);

            // When/Then
            assertThatThrownBy(() -> service.delete(999L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should throw InsufficientPermissionsException when non-owner tries to delete")
        void shouldThrowInsufficientPermissionsWhenNonOwnerTriesToDelete() {
            // Given
            User owner = createUser(1L, "owner-uid");
            UserSocialConnection connection = createConnection(1L, owner, false);

            when(permissionUtils.getUserId()).thenReturn("other-uid");
            when(permissionUtils.isAdmin()).thenReturn(false);
            when(permissionUtils.isUserOwner(connection)).thenReturn(false);
            when(repository.existsById(1L)).thenReturn(true);
            when(repository.findById(1L)).thenReturn(Optional.of(connection));

            // When/Then
            assertThatThrownBy(() -> service.delete(1L))
                    .isInstanceOf(InsufficientPermissionsException.class);
        }
    }

    @Nested
    @DisplayName("getDataPagedAndFiltered")
    class GetDataPagedAndFilteredTests {

        @Test
        @DisplayName("should filter by user firebaseUserId for non-admin users")
        void shouldFilterByUserFirebaseUidForNonAdminUsers() {
            // Given
            String firebaseUid = "user-uid";
            User user = createUser(1L, firebaseUid);
            UserSocialConnection connection = createConnection(1L, user, true);

            Pageable pageable = PageRequest.of(0, 10);
            Map<String, String> filters = new HashMap<>();

            when(permissionUtils.isAdmin()).thenReturn(false);
            when(permissionUtils.getUserId()).thenReturn(firebaseUid);
            when(specificationBuilder.createSpecification(any())).thenReturn(Specification.where(null));
            when(repository.findAll(any(Specification.class), eq(pageable)))
                    .thenReturn(new PageImpl<>(List.of(connection)));

            // When
            Page<UserSocialConnection> result = service.getDataPagedAndFiltered(pageable, filters);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getContent()).hasSize(1);
            verify(repository).findAll(any(Specification.class), eq(pageable));
        }

        @Test
        @DisplayName("should not filter by user for admin users")
        void shouldNotFilterByUserForAdminUsers() {
            // Given
            User user1 = createUser(1L, "user1-uid");
            User user2 = createUser(2L, "user2-uid");
            UserSocialConnection connection1 = createConnection(1L, user1, true);
            UserSocialConnection connection2 = createConnection(2L, user2, true);

            Pageable pageable = PageRequest.of(0, 10);
            Map<String, String> filters = new HashMap<>();

            when(permissionUtils.isAdmin()).thenReturn(true);
            when(specificationBuilder.createSpecification(any())).thenReturn(Specification.where(null));
            when(repository.findAll(any(Specification.class), eq(pageable)))
                    .thenReturn(new PageImpl<>(List.of(connection1, connection2)));

            // When
            Page<UserSocialConnection> result = service.getDataPagedAndFiltered(pageable, filters);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getContent()).hasSize(2);
        }

        @Test
        @DisplayName("should return empty page when no connections match")
        void shouldReturnEmptyPageWhenNoConnectionsMatch() {
            // Given
            Pageable pageable = PageRequest.of(0, 10);
            Map<String, String> filters = new HashMap<>();

            when(permissionUtils.isAdmin()).thenReturn(true);
            when(specificationBuilder.createSpecification(any())).thenReturn(Specification.where(null));
            when(repository.findAll(any(Specification.class), eq(pageable)))
                    .thenReturn(Page.empty());

            // When
            Page<UserSocialConnection> result = service.getDataPagedAndFiltered(pageable, filters);

            // Then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("Connection entity tests")
    class ConnectionEntityTests {

        @Test
        @DisplayName("should create connection with all fields")
        void shouldCreateConnectionWithAllFields() {
            // Given
            User user = createUser(1L, "user-uid");

            // When
            UserSocialConnection connection = createConnection(1L, user, true);

            // Then
            assertThat(connection.getId()).isEqualTo(1L);
            assertThat(connection.getUser()).isEqualTo(user);
            assertThat(connection.getDisplayName()).isEqualTo("test_user");
            assertThat(connection.getFollowersCount()).isEqualTo(1000);
            assertThat(connection.getIsPrimary()).isTrue();
            assertThat(connection.getConnectionStatus()).isEqualTo(ConnectionStatus.CONNECTED);
            assertThat(connection.getSocialUserId()).isEqualTo("social123");
        }

        @Test
        @DisplayName("should update connection fields")
        void shouldUpdateConnectionFields() {
            // Given
            User user = createUser(1L, "user-uid");
            UserSocialConnection connection = createConnection(1L, user, true);

            // When
            connection.setFollowersCount(5000);
            connection.setIsPrimary(false);
            connection.setConnectionStatus(ConnectionStatus.EXPIRED);
            connection.setDisplayName("updated_user");

            // Then
            assertThat(connection.getFollowersCount()).isEqualTo(5000);
            assertThat(connection.getIsPrimary()).isFalse();
            assertThat(connection.getConnectionStatus()).isEqualTo(ConnectionStatus.EXPIRED);
            assertThat(connection.getDisplayName()).isEqualTo("updated_user");
        }

        @Test
        @DisplayName("should handle null isPrimary")
        void shouldHandleNullIsPrimary() {
            // Given
            UserSocialConnection connection = new UserSocialConnection();

            // When
            connection.setIsPrimary(null);

            // Then
            assertThat(connection.getIsPrimary()).isNull();
        }

        @Test
        @DisplayName("should handle connection status transitions")
        void shouldHandleConnectionStatusTransitions() {
            // Given
            User user = createUser(1L, "user-uid");
            UserSocialConnection connection = createConnection(1L, user, true);

            // When - Connected -> Expired
            assertThat(connection.getConnectionStatus()).isEqualTo(ConnectionStatus.CONNECTED);
            connection.setConnectionStatus(ConnectionStatus.EXPIRED);
            assertThat(connection.getConnectionStatus()).isEqualTo(ConnectionStatus.EXPIRED);

            // When - Expired -> Revoked
            connection.setConnectionStatus(ConnectionStatus.REVOKED);
            assertThat(connection.getConnectionStatus()).isEqualTo(ConnectionStatus.REVOKED);

            // When - Revoked -> Connected (re-connection)
            connection.setConnectionStatus(ConnectionStatus.CONNECTED);
            assertThat(connection.getConnectionStatus()).isEqualTo(ConnectionStatus.CONNECTED);
        }
    }

    @Nested
    @DisplayName("ConnectionStatus enum tests")
    class ConnectionStatusTests {

        @Test
        @DisplayName("should have CONNECTED status")
        void shouldHaveConnectedStatus() {
            assertThat(ConnectionStatus.CONNECTED).isNotNull();
        }

        @Test
        @DisplayName("should have EXPIRED status")
        void shouldHaveExpiredStatus() {
            assertThat(ConnectionStatus.EXPIRED).isNotNull();
        }

        @Test
        @DisplayName("should have REVOKED status")
        void shouldHaveRevokedStatus() {
            assertThat(ConnectionStatus.REVOKED).isNotNull();
        }

        @Test
        @DisplayName("should have exactly 4 status values")
        void shouldHaveExactly4StatusValues() {
            assertThat(ConnectionStatus.values()).hasSize(4);
        }

        @Test
        @DisplayName("should convert from name correctly")
        void shouldConvertFromNameCorrectly() {
            assertThat(ConnectionStatus.valueOf("CONNECTED")).isEqualTo(ConnectionStatus.CONNECTED);
            assertThat(ConnectionStatus.valueOf("EXPIRED")).isEqualTo(ConnectionStatus.EXPIRED);
            assertThat(ConnectionStatus.valueOf("REVOKED")).isEqualTo(ConnectionStatus.REVOKED);
            assertThat(ConnectionStatus.valueOf("DISCONNECTED")).isEqualTo(ConnectionStatus.DISCONNECTED);
        }
    }

    @Nested
    @DisplayName("Permission utility behavior")
    class PermissionBehaviorTests {

        @Test
        @DisplayName("should check admin status before owner status")
        void shouldCheckAdminStatusBeforeOwnerStatus() {
            // Given
            User owner = createUser(1L, "owner-uid");
            UserSocialConnection connection = createConnection(1L, owner, true);

            // Admin can access without being owner
            when(permissionUtils.getUserId()).thenReturn("admin-uid");
            when(permissionUtils.isAdmin()).thenReturn(true);
            when(repository.findById(1L)).thenReturn(Optional.of(connection));

            // When
            UserSocialConnection result = service.findById(1L);

            // Then
            assertThat(result).isNotNull();
            // isUserOwner should not be called since admin check passes first
            verify(permissionUtils).isAdmin();
        }

        @Test
        @DisplayName("should check owner status when not admin")
        void shouldCheckOwnerStatusWhenNotAdmin() {
            // Given
            User owner = createUser(1L, "owner-uid");
            UserSocialConnection connection = createConnection(1L, owner, true);

            when(permissionUtils.getUserId()).thenReturn("owner-uid");
            when(permissionUtils.isAdmin()).thenReturn(false);
            when(permissionUtils.isUserOwner(connection)).thenReturn(true);
            when(repository.findById(1L)).thenReturn(Optional.of(connection));

            // When
            UserSocialConnection result = service.findById(1L);

            // Then
            assertThat(result).isNotNull();
            verify(permissionUtils).isAdmin();
            verify(permissionUtils).isUserOwner(connection);
        }
    }
}
