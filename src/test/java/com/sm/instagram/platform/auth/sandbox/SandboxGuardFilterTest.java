package com.sm.instagram.platform.auth.sandbox;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class SandboxGuardFilterTest {

    private final SandboxGuardFilter filter = new SandboxGuardFilter();

    private MockHttpServletResponse run(String method, String path) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, "/api" + path);
        request.setContextPath("/api");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        // A request that passed reaches the chain; the mock chain records it and leaves the status at 200.
        return response;
    }

    @ParameterizedTest(name = "{0} {1} passes through")
    @CsvSource({
            "POST, /test/auth/mock-session",
            "POST, /test/auth/clear-session",
            "GET, /users/me",
            "GET, /partnership-opportunity/paged",
            "GET, /actuator/health",
            "GET, /v3/api-docs",
    })
    @DisplayName("the two doors and everything outside /test pass")
    void openPaths(String method, String path) throws Exception {
        assertThat(run(method, path).getStatus()).isEqualTo(200);
    }

    @ParameterizedTest(name = "{0} {1} is closed")
    @CsvSource({
            "POST, /test/auth/ensure-user",
            "POST, /test/auth/set-account-status",
            "POST, /test/auth/reset-influencer-for-verification",
            "GET, /test/auth/mock-session",
            "POST, /test/legal/reset-consents",
            "DELETE, /test/legal/delete-documents-above-version",
            "POST, /test/registry/reset",
            "GET, /test/email/latest",
    })
    @DisplayName("every other test helper answers 404 as JSON")
    void closedPaths(String method, String path) throws Exception {
        MockHttpServletResponse response = run(method, path);
        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getContentAsString()).contains("\"status\":404").contains("/api" + path);
    }

    @Test
    @DisplayName("the context path is stripped before matching, so /api/test/... is /test/...")
    void contextPathStripped() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/test/auth/mock-session");
        request.setContextPath("/api");
        assertThat(SandboxGuardFilter.pathWithinApplication(request)).isEqualTo("/test/auth/mock-session");
        MockHttpServletRequest bare = new MockHttpServletRequest("POST", "/test/auth/mock-session");
        assertThat(SandboxGuardFilter.pathWithinApplication(bare)).isEqualTo("/test/auth/mock-session");
    }
}
