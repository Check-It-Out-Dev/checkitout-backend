package com.sm.instagram.platform.unit.config;

import com.sm.instagram.platform.config.FreeFormMapSchemaCustomizer;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MapSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code Map<String, Object>} means any value, and only that shape may be corrected.
 *
 * <p>Four endpoints returned {@code {"status":"healthy","service":"auth"}} against a document that
 * said every value in the map is an object. The correction is one line; the risk is that it also
 * hits a map whose values really are described, so most of what is asserted here is what the
 * customiser must leave alone.
 */
@DisplayName("Free-form maps are documented as accepting any value")
class FreeFormMapSchemaCustomizerUnitTest {

    private final FreeFormMapSchemaCustomizer customizer = new FreeFormMapSchemaCustomizer();

    /** What springdoc emits for {@code Map<String, Object>}. */
    private static Schema<?> freeFormMap() {
        return new MapSchema().additionalProperties(new ObjectSchema());
    }

    private static Content jsonOf(Schema<?> schema) {
        return new Content().addMediaType("application/json", new MediaType().schema(schema));
    }

    private OpenAPI customise(OpenAPI openApi) {
        customizer.describeFreeFormMaps().customise(openApi);
        return openApi;
    }

    private OpenAPI responding(Schema<?> schema) {
        Operation operation = new Operation().responses(new ApiResponses()
                .addApiResponse("200", new ApiResponse().description("ok").content(jsonOf(schema))));
        return customise(new OpenAPI().paths(new Paths()
                .addPathItem("/auth/health", new PathItem().get(operation))));
    }

    @Test
    @DisplayName("a Map<String, Object> response says any value is allowed")
    void freeFormResponseAcceptsAnything() {
        Schema<?> schema = freeFormMap();

        responding(schema);

        assertThat(schema.getAdditionalProperties()).isEqualTo(Boolean.TRUE);
    }

    @Test
    @DisplayName("a Map<String, SomeDto> keeps its $ref — the values really are described")
    void referencedValuesAreLeftAlone() {
        Schema<?> values = new Schema<>().$ref("#/components/schemas/UserDtoOut");
        Schema<?> schema = new MapSchema().additionalProperties(values);

        responding(schema);

        assertThat(schema.getAdditionalProperties()).isSameAs(values);
    }

    @Test
    @DisplayName("a map of maps keeps its inner map, and the inner map is corrected in place")
    void nestedMapsAreWalkedNotFlattened() {
        Schema<?> inner = freeFormMap();
        Schema<?> outer = new MapSchema().additionalProperties(inner);

        responding(outer);

        assertThat(outer.getAdditionalProperties()).isSameAs(inner);
        assertThat(inner.getAdditionalProperties()).isEqualTo(Boolean.TRUE);
    }

    @Test
    @DisplayName("a map whose values carry properties is a description, not a free-form map")
    void describedValuesAreLeftAlone() {
        Schema<?> values = new ObjectSchema().addProperty("status", new StringSchema());
        Schema<?> schema = new MapSchema().additionalProperties(values);

        responding(schema);

        assertThat(schema.getAdditionalProperties()).isSameAs(values);
    }

    @Test
    @DisplayName("the map is found inside a component schema, an array and a request body")
    void everyPlaceASchemaCanHideIsVisited() {
        Schema<?> inProperty = freeFormMap();
        Schema<?> inArray = freeFormMap();
        Schema<?> inBody = freeFormMap();
        Schema<?> component = new ObjectSchema()
                .addProperty("metadata", inProperty)
                .addProperty("rows", new ArraySchema().items(inArray));
        Operation operation = new Operation()
                .requestBody(new RequestBody().content(jsonOf(inBody)))
                .responses(new ApiResponses()
                        .addApiResponse("204", new ApiResponse().description("no content")));

        customise(new OpenAPI()
                .components(new Components().addSchemas("EventDtoIn", component))
                .paths(new Paths().addPathItem("/events", new PathItem().post(operation))));

        assertThat(inProperty.getAdditionalProperties()).isEqualTo(Boolean.TRUE);
        assertThat(inArray.getAdditionalProperties()).isEqualTo(Boolean.TRUE);
        assertThat(inBody.getAdditionalProperties()).isEqualTo(Boolean.TRUE);
    }

    @Test
    @DisplayName("a schema that contains itself terminates")
    void cyclicSchemasTerminate() {
        Schema<?> node = new ObjectSchema();
        node.addProperty("metadata", freeFormMap());
        node.addProperty("parent", node);

        customise(new OpenAPI()
                .components(new Components().addSchemas("TreeNodeDto", node))
                .paths(new Paths()));

        assertThat(node.getProperties().get("metadata").getAdditionalProperties())
                .isEqualTo(Boolean.TRUE);
    }

    @Test
    @DisplayName("an empty document is not a special case")
    void emptyDocumentIsSafe() {
        assertThat(customise(new OpenAPI()).getPaths()).isNull();
    }
}
