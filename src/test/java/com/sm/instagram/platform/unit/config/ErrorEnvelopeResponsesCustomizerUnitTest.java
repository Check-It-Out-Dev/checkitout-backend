package com.sm.instagram.platform.unit.config;

import com.sm.instagram.platform.config.ErrorEnvelopeResponsesCustomizer;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An error response describes the error, not the thing the endpoint returns when it works.
 *
 * <p>springdoc fills a response's schema from the method's return type, so the document claimed a
 * 404 from {@code /address/user/{userId}} returns an array of addresses. 203 responses said
 * something like that; not one of them named a genuine error type, which is what made replacing
 * them wholesale safe.
 */
@DisplayName("Every 4xx and 5xx describes the error envelope")
class ErrorEnvelopeResponsesCustomizerUnitTest {

    private static final String REF = "#/components/schemas/" + ErrorEnvelopeResponsesCustomizer.SCHEMA_NAME;

    private final ErrorEnvelopeResponsesCustomizer customizer = new ErrorEnvelopeResponsesCustomizer();

    private static Content schemaNamed(String ref) {
        return new Content().addMediaType("*/*", new MediaType().schema(new Schema<>().$ref(ref)));
    }

    private OpenAPI customise(Operation operation) {
        PathItem item = new PathItem();
        item.operation(PathItem.HttpMethod.GET, operation);
        OpenAPI openApi = new OpenAPI().paths(new Paths().addPathItem("/address/user/{userId}", item));
        customizer.documentErrorEnvelope().customise(openApi);
        return openApi;
    }

    private static String refOf(ApiResponse response) {
        return response.getContent().values().iterator().next().getSchema().get$ref();
    }

    @Test
    @DisplayName("a 404 carrying the success schema is corrected")
    void inheritedSuccessSchemaIsReplaced() {
        ApiResponse notFound = new ApiResponse()
                .description("User not found")
                .content(schemaNamed("#/components/schemas/AddressDtoOut"));
        Operation operation = new Operation().responses(new ApiResponses()
                .addApiResponse("200", new ApiResponse()
                        .content(schemaNamed("#/components/schemas/AddressDtoOut")))
                .addApiResponse("404", notFound));

        customise(operation);

        assertThat(refOf(notFound)).isEqualTo(REF);
        assertThat(notFound.getDescription())
                .as("the human description is the operation's own and is kept")
                .isEqualTo("User not found");
    }

    @Test
    @DisplayName("a 200 is never touched")
    void successIsLeftAlone() {
        ApiResponse ok = new ApiResponse().content(schemaNamed("#/components/schemas/AddressDtoOut"));
        Operation operation = new Operation().responses(new ApiResponses().addApiResponse("200", ok));

        customise(operation);

        assertThat(refOf(ok)).isEqualTo("#/components/schemas/AddressDtoOut");
    }

    @Test
    @DisplayName("an error response with no content at all gains the envelope")
    void describedErrorGainsContent() {
        ApiResponse unauthorised = new ApiResponse().description("No valid session");
        Operation operation = new Operation().responses(
                new ApiResponses().addApiResponse("401", unauthorised));

        customise(operation);

        assertThat(refOf(unauthorised)).isEqualTo(REF);
    }

    @Test
    @DisplayName("the envelope is registered once, from the class the handlers actually return")
    void envelopeIsRegistered() {
        OpenAPI openApi = customise(new Operation().responses(
                new ApiResponses().addApiResponse("500", new ApiResponse().description("boom"))));

        Schema<?> envelope = openApi.getComponents().getSchemas()
                .get(ErrorEnvelopeResponsesCustomizer.SCHEMA_NAME);
        assertThat(envelope).isNotNull();
        assertThat(envelope.getProperties().keySet())
                .as("the fields BaseExceptionHandler.ErrorResponse actually serialises")
                .contains("timestamp", "status", "error", "message", "path", "requestId");
    }

    @Test
    @DisplayName("the envelope's timestamp does not claim RFC 3339 either")
    void envelopeTimestampIsNotADateTime() {
        OpenAPI openApi = customise(new Operation().responses(
                new ApiResponses().addApiResponse("500", new ApiResponse().description("boom"))));

        Schema<?> timestamp = (Schema<?>) openApi.getComponents().getSchemas()
                .get(ErrorEnvelopeResponsesCustomizer.SCHEMA_NAME)
                .getProperties().get("timestamp");

        // LocalDateTimeSchemaConfig teaches springdoc this, but its registry is not consulted when
        // this class reads the model directly -- so the envelope arrived claiming a format the
        // handlers cannot satisfy, in the one schema every error response points at.
        //
        // Only the format is asserted. Whether ModelConverters emits one at all depends on whether
        // the field's @JsonFormat has already collapsed it to a plain string, which differs between
        // this isolated read and the generated document; the property that has to hold in both is
        // that no date-time survives. OpenApiDateTimeFormatUnitTest checks the document itself.
        assertThat(timestamp.getFormat())
                .as("it is a LocalDateTime and serialises with no UTC offset")
                .isNull();
    }

    @ParameterizedTest(name = "{0} -> error: {1}")
    @CsvSource({
            "200,false", "201,false", "204,false", "302,false",
            "400,true", "401,true", "404,true", "409,true", "429,true", "500,true", "507,true",
            "default,false", "2XX,false", "'',false",
    })
    @DisplayName("only a real status of 400 or more counts")
    void errorCodesAreRecognised(String code, boolean expected) {
        assertThat(ErrorEnvelopeResponsesCustomizer.isError(code)).isEqualTo(expected);
    }
}
