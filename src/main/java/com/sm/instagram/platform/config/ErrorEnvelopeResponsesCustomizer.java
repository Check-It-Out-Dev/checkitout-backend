package com.sm.instagram.platform.config;

import com.sm.instagram.platform.common.exceptions.handlers.BaseExceptionHandler;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;
import java.util.Map;

/**
 * Error responses describe the error envelope, not whatever the handler returns when it succeeds.
 *
 * <p>springdoc fills a response's schema from the method's return type unless the {@code @ApiResponse}
 * annotation supplies its own {@code content}. Almost none of them do, so the document said things
 * like "a 404 from {@code GET /address/user/{userId}} returns an array of AddressDtoOut". It returns
 * {@code {"timestamp":…,"status":404,"error":"Not Found","message":"User not found","path":…}}, like
 * every other error in this application, because they all come from the handlers in
 * {@code common.exceptions}.
 *
 * <p>203 responses across the document were wrong this way, every single one of them a domain DTO —
 * not one was a genuine error payload, which is what makes replacing them wholesale safe rather than
 * a guess. Schemathesis reported five of them as "response violates schema" in one night run; the
 * other 198 were wrong too, and had simply not been generated against yet.
 *
 * <p>Runs after the two customisers that ADD error responses ({@link AuthFailureResponsesCustomizer},
 * {@link ValidationFailureResponsesCustomizer}) so the ones they add are described as well. springdoc
 * applies customisers in bean order; this one takes anything with a status of 400 or more, whether it
 * was declared by hand, inherited, or added by a sibling.
 */
@Configuration
public class ErrorEnvelopeResponsesCustomizer {

    /** Name of the envelope in {@code components/schemas}. */
    public static final String SCHEMA_NAME = "ApiErrorResponse";

    private static final String SCHEMA_REF = "#/components/schemas/" + SCHEMA_NAME;

    @Bean
    public OpenApiCustomizer documentErrorEnvelope() {
        return openApi -> {
            if (openApi.getPaths() == null) {
                return;
            }
            registerEnvelope(openApi.getComponents() != null
                    ? openApi.getComponents()
                    : openApi.components(new Components()).getComponents());

            openApi.getPaths().values().forEach(item ->
                    item.readOperations().forEach(operation -> {
                        if (operation.getResponses() == null) {
                            return;
                        }
                        operation.getResponses().forEach((code, response) -> {
                            if (isError(code)) {
                                describeAsEnvelope(response);
                            }
                        });
                    }));
        };
    }

    /** Add the envelope to components, generated from the class the handlers actually return. */
    private void registerEnvelope(Components components) {
        if (components.getSchemas() != null && components.getSchemas().containsKey(SCHEMA_NAME)) {
            return;
        }
        Map<String, Schema> resolved =
                ModelConverters.getInstance().read(BaseExceptionHandler.ErrorResponse.class);
        Schema<?> envelope = resolved.get(BaseExceptionHandler.ErrorResponse.class.getSimpleName());
        if (envelope == null) {
            return;
        }
        envelope.setDescription(
                "The shape of every error this API returns. Produced by the handlers in "
                        + "common.exceptions, so it is the same for a rejected field, a missing row and "
                        + "an exhausted rate limit.");
        correctLocalDateTimeFormats(envelope);
        components.addSchemas(SCHEMA_NAME, envelope);
    }

    /**
     * Undo {@code format: date-time} on the fields that are {@link java.time.LocalDateTime}.
     *
     * <p>{@link LocalDateTimeSchemaConfig} teaches springdoc that a LocalDateTime is a string with no
     * format, because it carries no offset and RFC 3339 requires one. That registry is consulted when
     * springdoc builds schemas from controller signatures — and NOT when this class reads a model
     * directly through {@link ModelConverters}, which is how the envelope gets built. So the envelope
     * arrived claiming its {@code timestamp} is an RFC 3339 date-time, which is the very thing that
     * commit set out to stop, in the one schema every error response now points at.
     *
     * <p>Caught by {@code OpenApiDateTimeFormatUnitTest} on the first run after this class existed,
     * which is the argument for that test reading the committed document rather than trusting a
     * configuration to have been applied.
     */
    private void correctLocalDateTimeFormats(Schema<?> envelope) {
        Map<String, Schema> properties = envelope.getProperties();
        if (properties == null) {
            return;
        }
        properties.forEach((name, property) -> {
            if (!"date-time".equals(property.getFormat())) {
                return;
            }
            if (isLocalDateTime(name)) {
                property.setFormat(null);
                property.setExample(LocalDateTimeSchemaConfig.LOCAL_DATE_TIME_EXAMPLE);
            }
        });
    }

    /** True when the envelope's field of that name is declared as a {@link java.time.LocalDateTime}. */
    private static boolean isLocalDateTime(String fieldName) {
        try {
            return BaseExceptionHandler.ErrorResponse.class
                    .getDeclaredField(fieldName).getType() == java.time.LocalDateTime.class;
        } catch (NoSuchFieldException renamed) {
            return false;
        }
    }

    /**
     * The few error bodies that are deliberately not the envelope, by the name of their schema.
     *
     * <p>Overwriting is the right default and the reason this customiser exists: springdoc
     * inherits a method's return type onto its 4xx responses, so a 404 on an address endpoint was
     * published as an address. Those have to be replaced, and they carry a schema, so "it already
     * has one" cannot be the test.
     *
     * <p>What is left is a short list. {@code GET /auth/social/callback/instagram} answers a caller
     * it cannot redirect with {@code {success, error}} -- a shape older than the envelope, and the
     * one the mobile clients read. Declaring it on the operation had no effect until this existed:
     * the declaration was made and then replaced with a promise the endpoint does not keep.
     */
    private static final Set<String> OWN_ERROR_SHAPES = Set.of("OAuthCallbackFailure");

    private void describeAsEnvelope(ApiResponse response) {
        if (declaresItsOwnShape(response)) {
            return;
        }
        response.setContent(envelopeContent());
    }

    private static boolean declaresItsOwnShape(ApiResponse response) {
        Content content = response.getContent();
        if (content == null) {
            return false;
        }
        return content.values().stream()
                .map(MediaType::getSchema)
                .filter(schema -> schema != null && schema.get$ref() != null)
                .map(schema -> schema.get$ref().substring(schema.get$ref().lastIndexOf('/') + 1))
                .anyMatch(OWN_ERROR_SHAPES::contains);
    }

    /**
     * The content block every error response carries.
     *
     * <p>Public because the two customisers that CREATE error responses use it as they create them,
     * rather than relying on this one to come along afterwards. springdoc applies OpenApiCustomizer
     * beans in bean-name order, which is alphabetical, so `ErrorEnvelope…` runs before
     * `ValidationFailure…` and the 165 responses the latter adds were left with no schema at all.
     * An {@code @Order} annotation does not change that. Describing a response where it is created
     * is not a workaround for the ordering; it is simply the right place.
     */
    public static Content envelopeContent() {
        return new Content().addMediaType("application/json",
                new MediaType().schema(new Schema<>().$ref(SCHEMA_REF)));
    }

    /** A status of 400 or more. Anything else — including a default or a symbolic key — is left alone. */
    public static boolean isError(String code) {
        if (code == null || code.length() != 3) {
            return false;
        }
        try {
            return Integer.parseInt(code) >= 400;
        } catch (NumberFormatException notAStatus) {
            return false;
        }
    }
}
