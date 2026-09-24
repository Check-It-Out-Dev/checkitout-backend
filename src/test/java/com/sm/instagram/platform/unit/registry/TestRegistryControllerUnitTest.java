package com.sm.instagram.platform.unit.registry;

import com.sm.instagram.platform.auth.cache.UserCacheService;
import com.sm.instagram.platform.registry.CompanyDataRepository;
import com.sm.instagram.platform.registry.RegistryLookupService;
import com.sm.instagram.platform.registry.test.RegistryStubState;
import com.sm.instagram.platform.registry.test.TestRegistryController;
import com.sm.instagram.platform.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The E2E reset endpoint clears the lookup cache through the service, not by reflection. The
 * proxy half of that story is proven by RegistryLookupService_Lookup_IntegrationTest; this pins
 * the endpoint's own contract, which the frontend's "registry stubs are reset" step reads.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TestRegistryController.clearCache")
class TestRegistryControllerUnitTest {

    @Mock private RegistryStubState stubState;
    @Mock private UserRepository userRepository;
    @Mock private CompanyDataRepository companyDataRepository;
    @Mock private RegistryLookupService registryLookupService;
    @Mock private UserCacheService userCacheService;

    @InjectMocks private TestRegistryController controller;

    @Test
    @DisplayName("answers 200 with the number of entries the service removed")
    void reportsWhatTheServiceCleared() {
        when(registryLookupService.clearLookupCache()).thenReturn(2);

        ResponseEntity<Map<String, Object>> response = controller.clearCache();

        verify(registryLookupService).clearLookupCache();
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).containsEntry("cleared", true).containsEntry("entriesRemoved", 2);
    }
}
