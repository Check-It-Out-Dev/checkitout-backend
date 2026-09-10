package com.sm.instagram.platform.unit.config;

import com.sm.instagram.platform.config.AuthFailureResponsesCustomizer;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The customiser exists because the document disagreed with the server about what a secured
 * endpoint can return. These tests are what keeps the two in step when a matcher moves.
 */
class AuthFailureResponsesCustomizerUnitTest {

    private final AuthFailureResponsesCustomizer customizer = new AuthFailureResponsesCustomizer();

    @ParameterizedTest(name = "{1} {0} is public")
    @CsvSource({
            "/auth/login,POST",
            "/health/live,GET",
            "/system/info,GET",
            "/test/auth/mock-session,POST",
            "/dev-lite/upload/abc,PUT",
            "/support/ticket,POST",
            "/support/ticket/status,GET",
            "/support/faq/general,GET",
            "/support/ticket/42/attachments,POST",
    })
    @DisplayName("a path on a permitAll matcher is left alone")
    void publicPathsAreNotAnnotated(String path, String method) {
        assertThat(AuthFailureResponsesCustomizer.isPublic(path, PathItem.HttpMethod.valueOf(method))).isTrue();
    }

    @ParameterizedTest(name = "{1} {0} is secured")
    @CsvSource({
            "/activecoop/accept,GET",
            "/address/paged,GET",
            "/users/me,GET",
            "/admin/users,GET",
            // the same path that is public to POST authenticates to GET
            "/support/ticket,GET",
            // a prefix match must not leak: this is not /support/faq
            "/support/ticketing,POST",
    })
    @DisplayName("everything else is treated as secured")
    void securedPathsAreAnnotated(String path, String method) {
        assertThat(AuthFailureResponsesCustomizer.isPublic(path, PathItem.HttpMethod.valueOf(method))).isFalse();
    }

    @Test
    @DisplayName("a secured operation gains 401 and 403")
    void addsBothFailures() {
        OpenAPI api = documentWith("/users/me", new Operation().responses(ok()));

        customizer.documentAuthenticationFailures().customise(api);

        ApiResponses responses = api.getPaths().get("/users/me").getGet().getResponses();
        assertThat(responses.keySet()).contains("200", "401", "403");
        assertThat(responses.get("401").getDescription()).contains("No valid session");
    }

    @Test
    @DisplayName("an operation that already documents 401 keeps its own description")
    void doesNotOverwrite() {
        ApiResponses existing = ok();
        existing.addApiResponse("401", new ApiResponse().description("Step-up authentication required"));
        OpenAPI api = documentWith("/users/me", new Operation().responses(existing));

        customizer.documentAuthenticationFailures().customise(api);

        assertThat(api.getPaths().get("/users/me").getGet().getResponses().get("401").getDescription())
                .isEqualTo("Step-up authentication required");
    }

    @Test
    @DisplayName("a public operation gains nothing")
    void leavesPublicAlone() {
        OpenAPI api = documentWith("/auth/login", new Operation().responses(ok()));

        customizer.documentAuthenticationFailures().customise(api);

        assertThat(api.getPaths().get("/auth/login").getGet().getResponses().keySet()).containsExactly("200");
    }

    @Test
    @DisplayName("a document with no paths is not a crash")
    void toleratesAnEmptyDocument() {
        OpenAPI api = new OpenAPI();

        customizer.documentAuthenticationFailures().customise(api);

        assertThat(api.getPaths()).isNull();
    }

    private static OpenAPI documentWith(String path, Operation operation) {
        return new OpenAPI().paths(new Paths().addPathItem(path, new PathItem().get(operation)));
    }

    private static ApiResponses ok() {
        return new ApiResponses().addApiResponse("200", new ApiResponse().description("OK"));
    }
}
