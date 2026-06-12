package com.sm.instagram.platform.unit.service;

import com.sm.instagram.platform.common.exceptions.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for common exception classes.
 * Tests exception creation, message handling, and i18n support.
 */
@DisplayName("Common Exceptions Unit Tests")
class CommonExceptionsUnitTest {

    // ==================== TranslatableException Tests ====================

    @Nested
    @DisplayName("TranslatableException")
    class TranslatableExceptionTests {

        @Test
        @DisplayName("should create exception with message key only")
        void shouldCreateExceptionWithMessageKeyOnly() {
            // Given
            String messageKey = "error.generic.something_went_wrong";

            // When
            TranslatableException exception = new TranslatableException(messageKey);

            // Then
            assertThat(exception.getMessageKey()).isEqualTo(messageKey);
            assertThat(exception.getMessage()).isEqualTo(messageKey);
            assertThat(exception.getArgs()).isEmpty();
        }

        @Test
        @DisplayName("should create exception with message key and args")
        void shouldCreateExceptionWithMessageKeyAndArgs() {
            // Given
            String messageKey = "error.resource.not_found";
            String resourceName = "User";
            Long resourceId = 123L;

            // When
            TranslatableException exception = new TranslatableException(messageKey, resourceName, resourceId);

            // Then
            assertThat(exception.getMessageKey()).isEqualTo(messageKey);
            assertThat(exception.getArgs()).containsExactly(resourceName, resourceId);
        }

        @Test
        @DisplayName("should handle null args gracefully")
        void shouldHandleNullArgsGracefully() {
            // Given
            String messageKey = "error.test";

            // When
            TranslatableException exception = new TranslatableException(messageKey, (java.io.Serializable[]) null);

            // Then
            assertThat(exception.getArgs()).isEmpty();
        }

        @Test
        @DisplayName("should be instance of RuntimeException")
        void shouldBeInstanceOfRuntimeException() {
            // Given
            TranslatableException exception = new TranslatableException("error.test");

            // Then
            assertThat(exception).isInstanceOf(RuntimeException.class);
        }
    }

    // ==================== ResourceNotFoundException Tests ====================

    @Nested
    @DisplayName("ResourceNotFoundException")
    class ResourceNotFoundExceptionTests {

        @Test
        @DisplayName("should create exception with resource name")
        void shouldCreateExceptionWithResourceName() {
            // Given
            String resourceName = "Campaign";

            // When
            ResourceNotFoundException exception = new ResourceNotFoundException(resourceName);

            // Then
            assertThat(exception.getMessageKey()).isEqualTo("error.resource.not_found");
            assertThat(exception.getArgs()).containsExactly(resourceName);
        }

        @Test
        @DisplayName("should create exception with message key and args")
        void shouldCreateExceptionWithMessageKeyAndArgs() {
            // Given
            String messageKey = "error.resource.user_not_found";
            Long userId = 456L;

            // When
            ResourceNotFoundException exception = new ResourceNotFoundException(messageKey, userId);

            // Then
            assertThat(exception.getMessageKey()).isEqualTo(messageKey);
            assertThat(exception.getArgs()).containsExactly(userId);
        }

        @Test
        @DisplayName("should extend TranslatableException")
        void shouldExtendTranslatableException() {
            // Given
            ResourceNotFoundException exception = new ResourceNotFoundException("User");

            // Then
            assertThat(exception).isInstanceOf(TranslatableException.class);
        }

        @ParameterizedTest
        @ValueSource(strings = {"User", "Campaign", "PartnershipOpportunity", "ContentType", "Platform"})
        @DisplayName("should accept various resource names")
        void shouldAcceptVariousResourceNames(String resourceName) {
            // Given
            ResourceNotFoundException exception = new ResourceNotFoundException(resourceName);

            // Then
            assertThat(exception.getArgs()).containsExactly(resourceName);
        }
    }

    // ==================== ItemNotFoundException Tests ====================

    @Nested
    @DisplayName("ItemNotFoundException")
    class ItemNotFoundExceptionTests {

        @Test
        @DisplayName("should create exception with entity name")
        void shouldCreateExceptionWithEntityName() {
            // Given
            String entityName = "Order";

            // When
            ItemNotFoundException exception = new ItemNotFoundException(entityName);

            // Then
            assertThat(exception.getMessageKey()).isEqualTo("error.business.item_not_found");
            assertThat(exception.getArgs()).containsExactly(entityName);
        }

        @Test
        @DisplayName("should create exception with message key and args")
        void shouldCreateExceptionWithMessageKeyAndArgs() {
            // Given
            String messageKey = "error.item.not_found";
            String itemType = "Product";
            Long itemId = 789L;

            // When
            ItemNotFoundException exception = new ItemNotFoundException(messageKey, itemType, itemId);

            // Then
            assertThat(exception.getMessageKey()).isEqualTo(messageKey);
            assertThat(exception.getArgs()).containsExactly(itemType, itemId);
        }

        @Test
        @DisplayName("should extend TranslatableException")
        void shouldExtendTranslatableException() {
            // Given
            ItemNotFoundException exception = new ItemNotFoundException("Order");

            // Then
            assertThat(exception).isInstanceOf(TranslatableException.class);
        }
    }

    // ==================== BusinessRuleViolationException Tests ====================

    @Nested
    @DisplayName("BusinessRuleViolationException")
    class BusinessRuleViolationExceptionTests {

        @Test
        @DisplayName("should create exception with message only")
        void shouldCreateExceptionWithMessageOnly() {
            // Given
            String message = "error.business.cannot_apply_twice";

            // When
            BusinessRuleViolationException exception = new BusinessRuleViolationException(message);

            // Then
            assertThat(exception.getMessageKey()).isEqualTo(message);
            assertThat(exception.getRuleCode()).isNull();
            assertThat(exception.getContext()).isNull();
        }

        @Test
        @DisplayName("should create exception with message and rule code")
        void shouldCreateExceptionWithMessageAndRuleCode() {
            // Given
            String message = "error.business.invalid_status_transition";
            String ruleCode = "STATUS_TRANSITION_001";

            // When
            BusinessRuleViolationException exception = new BusinessRuleViolationException(message, ruleCode);

            // Then
            assertThat(exception.getMessageKey()).isEqualTo(message);
            assertThat(exception.getRuleCode()).isEqualTo(ruleCode);
            assertThat(exception.getContext()).isNull();
        }

        @Test
        @DisplayName("should create exception with message, rule code, and context")
        void shouldCreateExceptionWithAllFields() {
            // Given
            String message = "error.business.follower_count_too_low";
            String ruleCode = "FOLLOWER_001";
            String context = "Required: 10000, Actual: 5000";

            // When
            BusinessRuleViolationException exception = new BusinessRuleViolationException(message, ruleCode, context);

            // Then
            assertThat(exception.getMessageKey()).isEqualTo(message);
            assertThat(exception.getRuleCode()).isEqualTo(ruleCode);
            assertThat(exception.getContext()).isEqualTo(context);
        }

        @Test
        @DisplayName("should create exception with message and cause")
        void shouldCreateExceptionWithMessageAndCause() {
            // Given
            String message = "error.business.external_validation_failed";
            RuntimeException cause = new RuntimeException("Original error");

            // When
            BusinessRuleViolationException exception = new BusinessRuleViolationException(message, cause);

            // Then
            assertThat(exception.getMessageKey()).isEqualTo(message);
            assertThat(exception.getRuleCode()).isNull();
            // The cause is passed to TranslatableException as an arg (Serializable)
        }

        @Test
        @DisplayName("should extend TranslatableException")
        void shouldExtendTranslatableException() {
            // Given
            BusinessRuleViolationException exception = new BusinessRuleViolationException("error.test");

            // Then
            assertThat(exception).isInstanceOf(TranslatableException.class);
        }
    }

    // ==================== FollowerValidationException Tests ====================

    @Nested
    @DisplayName("FollowerValidationException")
    class FollowerValidationExceptionTests {

        @Test
        @DisplayName("should create exception with message")
        void shouldCreateExceptionWithMessage() {
            // Given
            String message = "error.validation.follower_count_insufficient";

            // When
            FollowerValidationException exception = new FollowerValidationException(message);

            // Then
            assertThat(exception.getMessageKey()).isEqualTo(message);
        }

        @Test
        @DisplayName("should create exception with message and cause")
        void shouldCreateExceptionWithMessageAndCause() {
            // Given
            String message = "error.validation.follower_count_check_failed";
            RuntimeException cause = new RuntimeException("API error");

            // When
            FollowerValidationException exception = new FollowerValidationException(message, cause);

            // Then
            assertThat(exception.getMessageKey()).isEqualTo(message);
        }

        @Test
        @DisplayName("should extend TranslatableException")
        void shouldExtendTranslatableException() {
            // Given
            FollowerValidationException exception = new FollowerValidationException("error.test");

            // Then
            assertThat(exception).isInstanceOf(TranslatableException.class);
        }
    }

    // ==================== InsufficientPermissionsException Tests ====================

    @Nested
    @DisplayName("InsufficientPermissionsException")
    class InsufficientPermissionsExceptionTests {

        @Test
        @DisplayName("should create exception with message only")
        void shouldCreateExceptionWithMessageOnly() {
            // Given
            String message = "error.permission.access_denied";

            // When
            InsufficientPermissionsException exception = new InsufficientPermissionsException(message);

            // Then
            assertThat(exception.getMessageKey()).isEqualTo(message);
            assertThat(exception.getUserId()).isNull();
            assertThat(exception.getOperation()).isNull();
            assertThat(exception.getResource()).isNull();
            assertThat(exception.getRequiredPermission()).isNull();
        }

        @Test
        @DisplayName("should create exception with user and operation")
        void shouldCreateExceptionWithUserAndOperation() {
            // Given
            String message = "error.permission.cannot_delete";
            String userId = "user123";
            String operation = "DELETE";

            // When
            InsufficientPermissionsException exception = new InsufficientPermissionsException(message, userId, operation);

            // Then
            assertThat(exception.getUserId()).isEqualTo(userId);
            assertThat(exception.getOperation()).isEqualTo(operation);
            assertThat(exception.getResource()).isNull();
        }

        @Test
        @DisplayName("should create exception with user, operation, and resource")
        void shouldCreateExceptionWithUserOperationAndResource() {
            // Given
            String message = "error.permission.cannot_modify";
            String userId = "user456";
            String operation = "UPDATE";
            String resource = "Campaign#123";

            // When
            InsufficientPermissionsException exception = new InsufficientPermissionsException(message, userId, operation, resource);

            // Then
            assertThat(exception.getUserId()).isEqualTo(userId);
            assertThat(exception.getOperation()).isEqualTo(operation);
            assertThat(exception.getResource()).isEqualTo(resource);
            assertThat(exception.getRequiredPermission()).isNull();
        }

        @Test
        @DisplayName("should create exception with all fields")
        void shouldCreateExceptionWithAllFields() {
            // Given
            String message = "error.permission.missing_role";
            String userId = "user789";
            String operation = "CREATE";
            String resource = "PartnershipOpportunity";
            String requiredPermission = "ROLE_COMPANY";

            // When
            InsufficientPermissionsException exception = new InsufficientPermissionsException(
                    message, userId, operation, resource, requiredPermission);

            // Then
            assertThat(exception.getUserId()).isEqualTo(userId);
            assertThat(exception.getOperation()).isEqualTo(operation);
            assertThat(exception.getResource()).isEqualTo(resource);
            assertThat(exception.getRequiredPermission()).isEqualTo(requiredPermission);
        }
    }

    // ==================== ExternalServiceException Tests ====================

    @Nested
    @DisplayName("ExternalServiceException")
    class ExternalServiceExceptionTests {

        @Test
        @DisplayName("should create exception with message and service name")
        void shouldCreateExceptionWithMessageAndServiceName() {
            // Given
            String message = "error.external.instagram_api_failed";
            String serviceName = "Instagram API";

            // When
            ExternalServiceException exception = new ExternalServiceException(message, serviceName);

            // Then
            assertThat(exception.getMessageKey()).isEqualTo(message);
            assertThat(exception.getServiceName()).isEqualTo(serviceName);
            assertThat(exception.getOperation()).isNull();
            assertThat(exception.getStatusCode()).isEqualTo(-1);
        }

        @Test
        @DisplayName("should create exception with message, service name, and operation")
        void shouldCreateExceptionWithOperation() {
            // Given
            String message = "error.external.firebase_auth_failed";
            String serviceName = "Firebase";
            String operation = "verifyToken";

            // When
            ExternalServiceException exception = new ExternalServiceException(message, serviceName, operation);

            // Then
            assertThat(exception.getServiceName()).isEqualTo(serviceName);
            assertThat(exception.getOperation()).isEqualTo(operation);
            assertThat(exception.getStatusCode()).isEqualTo(-1);
        }

        @Test
        @DisplayName("should create exception with status code")
        void shouldCreateExceptionWithStatusCode() {
            // Given
            String message = "error.external.api_error";
            String serviceName = "Payment Gateway";
            String operation = "processPayment";
            int statusCode = 502;

            // When
            ExternalServiceException exception = new ExternalServiceException(message, serviceName, operation, statusCode);

            // Then
            assertThat(exception.getServiceName()).isEqualTo(serviceName);
            assertThat(exception.getOperation()).isEqualTo(operation);
            assertThat(exception.getStatusCode()).isEqualTo(statusCode);
        }

        @Test
        @DisplayName("should create exception with cause")
        void shouldCreateExceptionWithCause() {
            // Given
            String message = "error.external.connection_timeout";
            String serviceName = "External API";
            RuntimeException cause = new RuntimeException("Connection refused");

            // When
            ExternalServiceException exception = new ExternalServiceException(message, serviceName, cause);

            // Then
            assertThat(exception.getServiceName()).isEqualTo(serviceName);
        }

        @Test
        @DisplayName("should create exception with operation and cause")
        void shouldCreateExceptionWithOperationAndCause() {
            // Given
            String message = "error.external.operation_failed";
            String serviceName = "Cloud Storage";
            String operation = "uploadFile";
            RuntimeException cause = new RuntimeException("Network error");

            // When
            ExternalServiceException exception = new ExternalServiceException(message, serviceName, operation, cause);

            // Then
            assertThat(exception.getServiceName()).isEqualTo(serviceName);
            assertThat(exception.getOperation()).isEqualTo(operation);
        }

        @ParameterizedTest
        @ValueSource(strings = {"Instagram API", "Firebase", "Google Cloud Storage", "Stripe", "SendGrid"})
        @DisplayName("should accept various service names")
        void shouldAcceptVariousServiceNames(String serviceName) {
            // Given
            ExternalServiceException exception = new ExternalServiceException("error.test", serviceName);

            // Then
            assertThat(exception.getServiceName()).isEqualTo(serviceName);
        }
    }

    // ==================== RateLimitTranslatableException Tests ====================

    @Nested
    @DisplayName("RateLimitTranslatableException")
    class RateLimitTranslatableExceptionTests {

        @Test
        @DisplayName("should create exception with message key")
        void shouldCreateExceptionWithMessageKey() {
            // Given
            String messageKey = "error.storage.rate_limit_exceeded";

            // When
            RateLimitTranslatableException exception = new RateLimitTranslatableException(messageKey);

            // Then
            assertThat(exception.getMessageKey()).isEqualTo(messageKey);
            assertThat(exception.getArgs()).isEmpty();
        }

        @Test
        @DisplayName("should create exception with args")
        void shouldCreateExceptionWithArgs() {
            // Given
            String messageKey = "error.api.rate_limit";
            String limit = "100";
            String period = "hour";

            // When
            RateLimitTranslatableException exception = new RateLimitTranslatableException(messageKey, limit, period);

            // Then
            assertThat(exception.getMessageKey()).isEqualTo(messageKey);
            assertThat(exception.getArgs()).containsExactly(limit, period);
        }

        @Test
        @DisplayName("should extend TranslatableException")
        void shouldExtendTranslatableException() {
            // Given
            RateLimitTranslatableException exception = new RateLimitTranslatableException("error.test");

            // Then
            assertThat(exception).isInstanceOf(TranslatableException.class);
        }
    }

    // ==================== Exception Hierarchy Tests ====================

    @Nested
    @DisplayName("Exception Hierarchy")
    class ExceptionHierarchyTests {

        @Test
        @DisplayName("all translatable exceptions should be catchable as RuntimeException")
        void allTranslatableExceptionsShouldBeCatchableAsRuntimeException() {
            // Given
            TranslatableException[] exceptions = {
                    new TranslatableException("test"),
                    new ResourceNotFoundException("Resource"),
                    new ItemNotFoundException("Item"),
                    new BusinessRuleViolationException("rule"),
                    new FollowerValidationException("followers"),
                    new InsufficientPermissionsException("permission"),
                    new ExternalServiceException("error", "Service"),
                    new RateLimitTranslatableException("rate")
            };

            // Then
            for (TranslatableException exception : exceptions) {
                assertThat(exception).isInstanceOf(RuntimeException.class);
            }
        }

        @Test
        @DisplayName("all exceptions should have message key accessible")
        void allExceptionsShouldHaveMessageKeyAccessible() {
            // Given
            String key = "error.test.key";

            TranslatableException[] exceptions = {
                    new TranslatableException(key),
                    new ResourceNotFoundException(key, "arg"),
                    new ItemNotFoundException(key, "arg"),
                    new BusinessRuleViolationException(key),
                    new FollowerValidationException(key),
                    new InsufficientPermissionsException(key),
                    new ExternalServiceException(key, "Service"),
                    new RateLimitTranslatableException(key)
            };

            // Then
            for (TranslatableException exception : exceptions) {
                assertThat(exception.getMessageKey()).isEqualTo(key);
            }
        }
    }

    // ==================== Edge Cases ====================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("should handle empty message key")
        void shouldHandleEmptyMessageKey() {
            // Given
            TranslatableException exception = new TranslatableException("");

            // Then
            assertThat(exception.getMessageKey()).isEmpty();
            assertThat(exception.getMessage()).isEmpty();
        }

        @Test
        @DisplayName("should handle very long message key")
        void shouldHandleVeryLongMessageKey() {
            // Given
            String longKey = "error." + "a".repeat(1000);
            TranslatableException exception = new TranslatableException(longKey);

            // Then
            assertThat(exception.getMessageKey()).isEqualTo(longKey);
        }

        @Test
        @DisplayName("should handle special characters in message key")
        void shouldHandleSpecialCharactersInMessageKey() {
            // Given
            String specialKey = "error.user.żółć_ęść";
            TranslatableException exception = new TranslatableException(specialKey);

            // Then
            assertThat(exception.getMessageKey()).isEqualTo(specialKey);
        }

        @Test
        @DisplayName("should handle multiple args of different types")
        void shouldHandleMultipleArgsOfDifferentTypes() {
            // Given
            String messageKey = "error.complex";
            String stringArg = "text";
            Long longArg = 123L;
            Integer intArg = 456;

            // When
            TranslatableException exception = new TranslatableException(messageKey, stringArg, longArg, intArg);

            // Then
            assertThat(exception.getArgs()).containsExactly(stringArg, longArg, intArg);
        }
    }
}
