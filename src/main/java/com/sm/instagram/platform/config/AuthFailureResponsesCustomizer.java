package com.sm.instagram.platform.config;

import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Locale;

/**
 * Declares the authentication and authorisation failures every secured operation can actually
 * return.
 *
 * <p>They were missing. Property-based fuzzing against a running server (Schemathesis, against the
 * public sandbox) reported "undocumented HTTP status code" on the first twelve secured operations it
 * touched: the server answers 401, the document says it cannot. That is a contract defect rather
 * than a cosmetic one — the frontend generates its client from this document, so a response every
 * caller has to handle was invisible to the generator, and any consumer reading the document would
 * conclude these endpoints are public.
 *
 * <p>Declaring them per controller would mean an annotation on several hundred methods and a new way
 * to forget one. This walks the finished document instead and adds the two responses to every
 * operation that is not on a public matcher, so the document follows the security configuration by
 * construction. An operation that already documents 401 or 403 keeps what it has.
 *
 * <p>The paths here mirror {@code WebSecurityConfiguration}'s {@code permitAll} matchers, minus the
 * {@code /api} prefix the OpenAPI server URL already carries. When a matcher changes there, it
 * changes here; {@code AuthFailureResponsesCustomizerUnitTest} is what notices.
 */
@Configuration
public class AuthFailureResponsesCustomizer {

    /** Prefixes served without authentication, relative to the document's {@code /api} server. */
    private static final List<String> PUBLIC_PREFIXES = List.of(
            "/actuator/health",
            "/health/",
            "/system/",
            "/auth/",
            "/error",
            "/swagger",
            "/v3/api-docs",
            "/dev-lite/",
            "/test/");

    /** Public only for one method; the same path authenticates for the others. */
    private static final List<String> PUBLIC_GET = List.of(
            "/support/ticket/status",
            "/support/ticket/access",
            "/support/faq");

    private static final List<String> PUBLIC_POST = List.of(
            "/support/ticket",
            "/support/ticket/response");

    @Bean
    public OpenApiCustomizer documentAuthenticationFailures() {
        return openApi -> {
            if (openApi.getPaths() == null) {
                return;
            }
            openApi.getPaths().forEach((path, item) ->
                    item.readOperationsMap().forEach((method, operation) -> {
                        if (isPublic(path, method)) {
                            return;
                        }
                        ApiResponses responses = operation.getResponses();
                        if (responses == null) {
                            return;
                        }
                        responses.computeIfAbsent("401", key -> new ApiResponse()
                                .description("No valid session: the request carried no credentials, "
                                        + "or they had expired.")
                                .content(ErrorEnvelopeResponsesCustomizer.envelopeContent()));
                        responses.computeIfAbsent("403", key -> new ApiResponse()
                                .description("Authenticated, but not allowed to perform this operation.")
                                .content(ErrorEnvelopeResponsesCustomizer.envelopeContent()));
                    }));
        };
    }

    public static boolean isPublic(String path, PathItem.HttpMethod method) {
        String normalised = path.toLowerCase(Locale.ROOT);
        if (PUBLIC_PREFIXES.stream().anyMatch(normalised::startsWith)) {
            return true;
        }
        if (method == PathItem.HttpMethod.GET && PUBLIC_GET.stream().anyMatch(normalised::startsWith)) {
            return true;
        }
        // /support/ticket is public to POST and authenticated to GET, so the match is exact rather
        // than a prefix - /support/ticket/{id} is not public.
        return method == PathItem.HttpMethod.POST
                && (PUBLIC_POST.contains(normalised) || normalised.matches("/support/ticket/[^/]+/attachments"));
    }
}
