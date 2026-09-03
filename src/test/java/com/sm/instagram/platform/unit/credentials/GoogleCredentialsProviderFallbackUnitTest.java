package com.sm.instagram.platform.unit.credentials;

import com.sm.instagram.platform.common.credentials.GoogleCredentialsProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins the contributor-boot guarantee: a clone with NO base64 property, NO
 * service-account.json and NO Application Default Credentials must still
 * initialize the provider in a non-production environment (synthetic
 * offline credentials, Priority 4). Without it the whole test context dies
 * through jwtAuthenticationFilter → sessionSecurity → geoLocation (the
 * public-snapshot 887-error cascade).
 */
@DisplayName("GoogleCredentialsProvider cred-less fallback")
class GoogleCredentialsProviderFallbackUnitTest {

    @Test
    @DisplayName("non-production init succeeds with no credential source at all")
    void credLessNonProductionInitSucceeds() {
        GoogleCredentialsProvider provider = new GoogleCredentialsProvider();
        Resource missing = mock(Resource.class);
        when(missing.exists()).thenReturn(false);
        ReflectionTestUtils.setField(provider, "serviceAccountJsonBase64", "");
        ReflectionTestUtils.setField(provider, "serviceAccountFile", missing);
        ReflectionTestUtils.setField(provider, "configuredProjectId", "");
        ReflectionTestUtils.setField(provider, "gcpProjectId", "check-it-out-47c50");
        ReflectionTestUtils.setField(provider, "appEnvironment", "TEST");

        assertThatCode(provider::init).doesNotThrowAnyException();

        assertThat(provider.getCachedCredentials()).isNotNull();
        assertThat(provider.getProjectId()).isNotBlank();
        assertThat(provider.isUsingProductionCredentials()).isFalse();
    }
}
